# UNIT-03 RDBMSセットアップ - Frontend Components Summary

`unit-03-code-generation-plan.md` Section 11〜13の実行結果サマリ。

## 実装場所の訂正（Part 1計画時に発見済み）

Functional Design（frontend-components.md）は当初、新規モジュール`frontend/src/rdbms-connection/`・パス`/rdbms-connections`を想定していたが、UNIT-02のCode Generationで実際に確立された規約（フラットな`frontend/src/pages/`・`frontend/src/api/`構成）と、UNIT-01由来で既に予約済みのナビゲーションパス（`design-system/components/navigation.ts`の`NAV_ROUTES`、`key: 'connections'`, `path: '/connections'`）に合わせて実装した（frontend-components.mdに訂正注記を追加済み）。

## APIクライアント

| ファイル | 役割 |
|---|---|
| `frontend/src/api/rdbmsConnections.ts` | 一覧・登録・更新・削除・接続テスト（保存済み/未保存）・スキーマ取込・スキーマ取得の各関数。`http.ts`の`apiFetch`を利用。`DEFAULT_PORT_BY_DB_TYPE`（dbType選択時のデフォルトポート自動入力用の静的定義。バックエンドの`RdbmsDialectStrategy`には追加していないため、フロントエンド側にのみ保持） |

## 画面

| ファイル | 対応するfrontend-components.md | 概要 |
|---|---|---|
| `pages/RdbmsConnectionListPage.tsx` | §1 | 接続一覧・登録編集フォームModal・削除確認（BR-RDBMS-09カスケード削除の明示）・接続テスト（保存済み/フォーム内未保存）・スキーマ取込アクション。`dbType`選択時のデフォルトポート自動入力（新規登録時のみ、既存値は上書きしない）、`schemaName`欄はPostgreSQL/H2選択時のみ表示、`additionalParams`のヘルプテキストはH2選択時のみ`;`区切りの記法例に切替 |
| `pages/SchemaDetailPage.tsx` | §2 | スキーマ詳細（読取専用）。テーブル一覧・選択中テーブルのカラム一覧（型・正規化型・NULL許容・制約バッジ）の2段DataTable。未取込の場合は案内メッセージ＋一覧への戻り導線 |

`App.tsx`のルーティングに`/connections`（`RdbmsConnectionListPage`）・`/connections/:id/schema`（`SchemaDetailPage`）を`ProtectedRoute`配下として追加。`HomePage.tsx`の`IMPLEMENTED_KEYS`に`'connections'`を追加し、トップ画面の「RDBMS接続設定」カードを活性化した。

## i18nリソース追加

`i18n/locales/{ja,en}/common.json`に`connections`セクション（画面タイトル・フォームラベル・エラーメッセージ分類文言等、約45キー）を追加。既存の`common`名前空間内でのプレフィックス運用を踏襲。

## 既存テストへの影響

`HomePage.test.tsx`が「準備中」バッジ数を7件とハードコード検証していたが、`connections`の活性化により6件に変化するため更新した（実装済みカードとして`feature-card-connections`の存在・クリック時の遷移も追加検証）。

## テスト結果

Vitest + React Testing Library。

| ファイル | テスト数 | 主な検証内容 |
|---|---|---|
| `RdbmsConnectionListPage.test.tsx` | 7 | 一覧表示・未取込バッジ、フォームのデフォルトポート自動入力・`schemaName`欄の出し分け、登録、フォーム内接続テスト（モーダルを閉じない）、削除確認ダイアログ、スキーマ取込、一覧取得失敗時のエラー表示 |
| `SchemaDetailPage.test.tsx` | 2 | 取込済み時のテーブル・カラム・制約表示、未取込時（`SCHEMA_NOT_IMPORTED`）の案内表示 |
| `rdbmsConnections.test.ts` | 8 | 各APIクライアント関数が正しいHTTPメソッド・パス・ボディで`apiFetch`を呼ぶこと |
| `HomePage.test.tsx`（更新） | 3 | 実装済みカード数変化の追従、`connections`カードのクリック遷移 |

`npm test`で全123件成功（既存UNIT-01/02分含む）、`npm run build`（`tsc -b && vite build`）・`npm run lint`も成功。
