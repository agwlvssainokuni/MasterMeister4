# UNIT-02 ユーザ登録・認証 - Frontend Components Summary

`unit-02-code-generation-plan.md` Section 13〜14の実行結果サマリ。

## 認証基盤

| ファイル | 役割 |
|---|---|
| `frontend/src/auth/tokenStorage.ts` | アクセストークン・リフレッシュトークンの読み書き（`window.sessionStorage`、nfr-design-patterns.md §3.1により両トークンともCookieではなくsessionStorageに保管） |
| `frontend/src/auth/jwt.ts` | `decodeJwtEmail()`。アクセストークンの`email`クレームを表示用に取り出す（署名検証はしない。検証はサーバ側の責務） |
| `frontend/src/auth/AuthContext.tsx` | `AuthProvider`/`useAuth()`。`isAuthenticated`状態、`login`/`logout`。`UNAUTHORIZED_EVENT`購読でトークン失効時に未認証状態へ遷移する |
| `frontend/src/auth/ProtectedRoute.tsx` | 未認証時は`/login`へリダイレクト（frontend-components.md §7） |
| `frontend/src/api/http.ts` | `apiFetch<T>()`。`Accept-Language`ヘッダー常時付与（NFR-7.3）、`auth:true`指定時はBearerヘッダー付与＋401時に1度だけリフレッシュ再試行。再試行も失敗した場合はトークンをクリアし`mastermeister:unauthorized`のDOMイベントを発行する（http.tsとAuthContextの循環依存を避けるための疎結合） |
| `frontend/src/api/{auth,registrations,adminUsers}.ts` | `apiFetch`の薄いラッパー（エンドポイントごとの型付きクライアント） |

## 画面

| ファイル | 対応するfrontend-components.md | 概要 |
|---|---|---|
| `pages/LoginPage.tsx` | §1 | ログイン。BR-REG-04によりメール不存在・パスワード不一致は同一エラーメッセージ（サーバ側で解決済みの文言をそのまま表示） |
| `pages/RegisterStep1Page.tsx` | §2 | ユーザ登録（メールアドレスのみ）。送信後は既存有無で分岐せず一律「送信完了」表示 |
| `pages/RegisterStep2Page.tsx` | §3 | パスワード設定（氏名・言語設定を含む、BR-REG-05）。`?token=`欠落時は無効なリンク表示 |
| `pages/UserManagementPage.tsx` | §4 | ユーザ管理。承認/却下/無効化/再有効化を単一画面で扱う（却下済みユーザの再承認も含む）。ステータスフィルタ（初期値PENDING）＋キーワード絞り込み |
| `pages/HomePage.tsx` + `pages/FeatureCard.tsx` | §5 | トップ画面。`NAV_ROUTES`（8項目）に対応するカードグリッドを表示し、実装済みは「ユーザ管理」のみ活性・遷移可能、他7項目は非活性＋「準備中」バッジ |
| `pages/AuthenticatedLayout.tsx` | §6 | `AppShell`のラッパー。JWTのemailクレームをヘッダーのユーザ表示名に使用し、ログアウト導線を実装（UNIT-01では未実装のプレースホルダーだった） |

`App.tsx`のルーティングを実ルートへ更新（`/login`・`/register`・`/register/complete`は公開ルート、`/`・`/users`は`AuthProvider`＋`ProtectedRoute`配下）。devビルド限定の`/mock/*`ルートは維持。

## i18nリソース追加

`design-system/i18n/locales/{ja,en}/common.json`に`auth`・`registration`・`users`・`home`（`home.card.*` 8キー含む）を追加。

## UNIT-01由来の不整合の修正

実装中に以下2件を発見し、UNIT-02の設計決定と整合させる形で修正した。

- **`navigation.ts`の`NAV_ROUTES`に残っていた`nav.dashboard`エントリ**: UNIT-01時点の「ダッシュボード」ナビ項目が、UNIT-02のFunctional Designで決定した「ダッシュボード→ユーザ管理への統合」（`aidlc-docs/construction/unit-01/functional-design/frontend-components.md`の訂正済み）に反映されずに残存していた。エントリと、孤立した`design-system.json`の`nav.dashboard`翻訳キーを削除（grepで他参照が無いことを確認。UNIT-01のdevビルド限定モック`DashboardMock.tsx`が使う`mock.dashboard.*`は無関係のため維持）。
- **`UserManagementPage.tsx`の`DataTable` `rowKey`型不一致**: `rowKey`は`string`を返す契約だが`(user) => user.id`（`number`）を渡しており、`tsc -b`でのみ検出されるビルドエラーだった（`npm run dev`のトランスパイルのみでは顕在化しない）。`String(user.id)`に修正。

## テスト結果

| ファイル | テスト数 | 主な検証内容 |
|---|---|---|
| `auth/tokenStorage.test.ts` | 3 | 未設定時null、set/get往復、clear |
| `auth/jwt.test.ts` | 4 | emailクレーム抽出、クレーム欠落・ペイロード欠落・JSON不正の異常系 |
| `api/http.test.ts` | 6 | 正常応答、204、エラー応答のApiError化、auth:trueでのBearer付与、401時のリフレッシュ再試行成功、リフレッシュも失敗した場合のトークンクリア＋`UNAUTHORIZED_EVENT`発行 |
| `auth/AuthContext.test.tsx` | 6 | Provider外呼び出しエラー、起動時の初期化、login、logout（サーバ失効成功/失敗）、`UNAUTHORIZED_EVENT`購読 |
| `auth/ProtectedRoute.test.tsx` | 2 | 未認証時リダイレクト、認証済み時の表示 |
| `pages/AuthenticatedLayout.test.tsx` | 2 | JWTのemailクレーム表示、ログアウト導線 |
| `pages/LoginPage.test.tsx` | 3 | フォーム表示、成功時遷移、失敗時エラー表示 |
| `pages/RegisterStep1Page.test.tsx` | 2 | 送信完了表示、エラー表示 |
| `pages/RegisterStep2Page.test.tsx` | 4 | token欠落、パスワード不一致、成功、エラー表示 |
| `pages/UserManagementPage.test.tsx` | 6 | 初期PENDINGフィルタ、フィルタ変更、キーワード絞り込み、承認確認フロー、キャンセル、一覧取得エラー |
| `pages/HomePage.test.tsx` | 2 | カード表示（準備中バッジ含む）、実装済みカードのクリック遷移 |

**合計**: 11ファイル・40テストケース（新規分）、`npm test`実行で既存65件と合わせて計105件すべて成功。加えて`npx tsc --noEmit`・`npm run lint`（oxlint）・`npm run build`すべて成功を確認。

## テスト用共通ヘルパー

`frontend/src/test/render.tsx`に`renderPage()`を追加（UNIT-01の`renderMock()`と並置）。`ThemeProvider`＋`MemoryRouter`（`initialEntries`指定可）＋`AuthProvider`でラップする。UNIT-02の画面は`useAuth`（`AuthContext`）に依存するため、UNIT-01のモック画面向け`renderMock()`とは別に用意した。

## 実装時のトラブルシューティング（後続ユニットへの申し送り）

- **`vi.restoreAllMocks()`は`vi.mock()`によるモジュール自動モックの呼び出し履歴をクリアしない**: `vi.restoreAllMocks()`は`vi.spyOn`で作成したスパイのみが対象で、`vi.mock('../api/xxx')`（ファクトリなし）で生成される自動モック関数の`mock.calls`は対象外。そのため`afterEach`で`vi.restoreAllMocks()`を呼んでも、前のテストでの呼び出し回数が次のテストに持ち越され、`toHaveBeenCalledTimes()`のような回数アサーションが誤った値になる（実際に`UserManagementPage.test.tsx`で発生し、`listUsers`の呼び出し回数が2回のはずが6回とカウントされた）。`vi.mock()`したモジュールを使うテストでは`afterEach`に`vi.resetAllMocks()`（または`vi.clearAllMocks()`）を使うこと。
- **UNIT-01時点の設計変更（ダッシュボード→ユーザ管理統合等）は、実装に反映されているかをコード全体でgrep確認する**: ドキュメント（frontend-components.md）は訂正されていても、既存実装ファイル（`navigation.ts`）側の追従漏れが発生し得る。UNIT間で共有するコンポーネント・定数を変更する際は、ドキュメントの修正だけでなく実装側の該当箇所も合わせて確認する。
