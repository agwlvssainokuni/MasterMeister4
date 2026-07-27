# MasterMeister frontend

React 19 + TypeScript + Vite製のフロントエンド。UNIT-01（デザインシステム基盤）で構築した共通コンポーネント・グランドデザインは`src/design-system/`配下、UNIT-02（ユーザ登録・認証）で構築したログイン・ユーザ登録・ユーザ管理・トップ画面、UNIT-03（RDBMSセットアップ）で構築したRDBMS接続設定・スキーマ詳細画面、UNIT-04（アクセス制御）で構築したグループ管理・権限設定画面、UNIT-05（マスタメンテナンス）で構築した接続選択・テーブル一覧・レコード一覧画面、UNIT-06（クエリ保存・実行）で構築した保存クエリ管理・ad-hocクエリ実行画面は`src/pages/`配下にある。

## 開発

バックエンド（`../backend`）を別プロセスで起動しておく必要がある（デフォルトport 8080。起動方法は`../backend/README.md`参照）。

```bash
npm install
npm run dev
```

`http://localhost:5173/`でアプリが起動する。devサーバは`/api/**`へのリクエストを`http://localhost:8080`へプロキシする（`vite.config.ts`の`server.proxy`）。本番ビルド（単一WAR）ではフロントエンドとバックエンドが同一オリジンから配信されるためプロキシは不要になる。

devビルド時のみ`/mock/*`配下でデザインシステムのコンポーネントカタログ・代表画面モックを確認できる（`/mock/catalog`が入口）。本番ビルドにはこれらのコードは含まれない。

## ビルド

```bash
npm run build
```

`dist/`に成果物を生成する。バックエンドとの統合（単一WAR生成）は`../backend`のGradleタスク（`./gradlew :backend:bootWar`）から行う。`frontend`単体のビルドはこのコマンドで完結し、バックエンドを必要としない。

## テスト

```bash
npm test        # 1回実行
npm run test:watch  # watchモード
```

Vitest + React Testing Libraryを使用。

## Lint / フォーマット

```bash
npm run lint          # oxlint
npm run format        # prettier --write
npm run format:check  # prettier --check
```

コードスタイルはセミコロンなし・シングルクォート（`.prettierrc.json`参照）。

## ディレクトリ構成

```
src/
├── design-system/     # UNIT-01で構築した共通デザインシステム
│   ├── tokens/         # デザイントークン、セルフホストフォント
│   ├── theme/           # ダークモード（ThemeProvider）
│   └── components/    # 共通UIコンポーネント一式
├── i18n/                 # 多言語対応（react-i18next、common/design-system名前空間。アプリ全体で使う横断的インフラのためdesign-system/の外に配置）
├── auth/                # 認証状態管理（AuthContext）、トークン保管（sessionStorage）、JWTデコード
├── api/                  # バックエンドAPIクライアント（apiFetch、リフレッシュ自動再試行）
├── pages/               # UNIT-02（ログイン、ユーザ登録、ユーザ管理、トップ）・UNIT-03（RDBMS接続設定、スキーマ詳細）・UNIT-04（グループ管理、権限設定）・UNIT-05（マスタメンテナンス3画面）・UNIT-06（保存クエリ管理3画面、ad-hocクエリ実行2画面、共有のQueryEditorPanel）・UNIT-07（クエリビルダー2画面＋タブサブコンポーネント7種＋共有のQueryBuilderOperandPicker）・UNIT-08（クエリ履歴2画面）・UNIT-09（監査ログ閲覧1画面）で構築した画面
├── mocks/              # devビルド限定のコンポーネントカタログ・代表画面モック
└── test/                 # テスト共通セットアップ・ヘルパー（renderMock、renderPage）
```

詳細は`aidlc-docs/construction/unit-01/code/component-inventory.md`（デザインシステム）・`aidlc-docs/construction/unit-0{2,3,4,5,6,7,8}/code/frontend-summary.md`（認証基盤・画面）を参照。

## クエリ履歴（UNIT-08）

`/query-history`画面で、実行済みクエリの履歴を閲覧・絞込できる。接続選択→履歴一覧の2画面構成。実行者スコープ（「全ユーザ」／「自分のみ」）の切替は管理者ユーザにのみ表示する。管理者判定は既存の`decodeJwtEmail`（`auth/jwt.ts`）と同じ設計思想の`decodeJwtRole`で行う（`AuthContext`自体はロール情報を持たないため、各ページで`getAccessToken()`と組み合わせて呼び出す）。

## 監査ログ閲覧（UNIT-09）

`/audit-log`画面で、監査ログを閲覧・絞込できる。UNIT-05〜08と異なり単一画面構成（接続選択画面なし）で、対象接続も他の絞込条件と並列の1つとして扱う（`connectionId`を持たないログインイベント等も同一画面で扱うため）。対象ユーザ・対象接続セレクタの選択肢は新規APIクライアントを追加せず、既存の`listUsers()`（`api/adminUsers.ts`）・`listConnections()`（`api/rdbmsConnections.ts`）をそのまま再利用する。管理者専用画面だが、フロントエンド側に独自のロール判定・ガードは設けない（`GroupManagementPage`等の既存の管理者専用画面と同じ方針で、アクセス制御はバックエンドの403応答に委ねる）。画面遷移導線・詳細モーダルは設けない。
