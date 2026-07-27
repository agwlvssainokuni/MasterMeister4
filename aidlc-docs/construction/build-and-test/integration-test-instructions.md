# Integration Test Instructions

## Purpose

各ユニット単体の単体テストではカバーしきれない、ユニット間の連携（データフロー、権限伝播、監査ログの整合性等）を検証する。本プロジェクトでは各ユニットのCode Generation完了時点で、devenv（PostgreSQL/MySQL/MariaDB）に対する実機E2E検証（curl経由のAPI呼び出し）として、実質的な統合テストを継続的に実施してきた。本ドキュメントはその手順を体系的に整理し、再現可能な形にまとめる。

## Setup Integration Test Environment

### 1. devenvの起動

```bash
cd devenv
docker compose up -d
docker compose ps   # mailpit, mysql, mariadb, postgres が起動していることを確認
```

### 2. アプリケーションの起動（内部DBはH2、対象RDBMS接続は別）

**重要**: アプリケーション自体のメタデータDB（ユーザ・接続設定・監査ログ等）は常にH2（デフォルト設定）を使う。devenvのPostgreSQL/MySQL/MariaDBは、UNIT-03で登録する「クエリ対象のRDBMS接続」であり、アプリ自体の内部DBとは別物（UNIT-09実機検証時に混同した教訓、`feedback_deployment_artifact.md`参照）。

```bash
./gradlew :backend:bootWar
mkdir -p /tmp/mm-integration-test-data
java -jar backend/build/libs/mastermeister-*.war \
  --MM_APP_DATASOURCE_PATH=/tmp/mm-integration-test-data/mastermeister \
  --MM_APP_JWT_SECRET=test-secret-key-at-least-32-bytes-long-for-hs256 \
  --MM_APP_ADMIN_BOOTSTRAP_EMAIL=admin@example.com \
  --MM_APP_ADMIN_BOOTSTRAP_PASSWORD=<複雑なパスワード> \
  --MM_APP_RDBMS_ENCRYPTION_KEYS=1:MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE= \
  --server.port=18080
```

`MM_APP_ADMIN_BOOTSTRAP_PASSWORD`は既知漏洩パスワードリストでの検証（SECURITY-12）に引っかからない、記号を含む複雑な文字列を指定する。

## Test Scenarios

### Scenario 1: ユーザ登録・承認 → ログイン（UNIT-02）

- **Description**: 一般ユーザの登録申請から管理者承認、ログインまでの一連のフローを確認する
- **Test Steps**:
  1. `POST /api/registrations`（`{email, language}`）でユーザ登録を申請
  2. Mailpit（`http://localhost:8025`）で確認メールのトークンを取得
  3. `POST /api/registrations/{token}/complete`（`{fullName, preferredLanguage, password}`）で登録完了
  4. 管理者トークンで`POST /api/admin/users/{id}/approve`を実行
  5. `POST /api/auth/login`で一般ユーザとしてログインしJWTを取得
- **Expected Results**: 各ステップがすべて200/204で成功し、最終的に一般ユーザのアクセストークンが取得できる

### Scenario 2: 接続登録 → スキーマ取込 → 権限設定 → マスタデータ表示（UNIT-03 → UNIT-04 → UNIT-05）

- **Description**: RDBMS接続を登録しスキーマを取り込んだ後、権限設定に応じてマスタデータの表示・編集可否が正しく制御されることを確認する
- **Test Steps**:
  1. 管理者で`POST /api/admin/rdbms-connections`により対象RDBMS（devenvのPostgreSQL等）を登録
  2. `POST /api/admin/rdbms-connections/{id}/schema-refresh`でスキーマを取り込む
  3. `POST /api/admin/permissions`等で一般ユーザ・グループに主権限（NONE/READ/UPDATE）・補助権限（CREATE/DELETE）を設定
  4. 一般ユーザで`GET /api/master-data/{connectionId}/{schema}/{table}`を呼び出し、設定した権限どおりに表示・編集可否が反映されることを確認
- **Expected Results**: 権限設定どおりの実効権限（`EffectivePermissionResolver`）が反映され、`NONE`権限のカラムは表示されない、`READ`未満のテーブルへの書き込みは拒否される

### Scenario 3: クエリビルダー → クエリ実行 → クエリ履歴（UNIT-07 → UNIT-06 → UNIT-08）

- **Description**: クエリビルダーで組み立てたSQLがクエリ実行画面・保存クエリへ連携され、実行履歴として記録・閲覧できることを確認する
- **Test Steps**:
  1. `GET /api/query-builder/{connectionId}/tables`等でテーブル一覧を取得しクエリを組み立てる（フロントエンドのrouter state連携はUIでの確認が必要、バックエンドAPIとしては`POST /api/query-builder/{connectionId}/generate-sql`でSQL生成を確認）
  2. 生成したSQLで`POST /api/queries/{connectionId}/execute`を実行
  3. `GET /api/query-history/{connectionId}`で実行履歴に記録されていることを確認（`queryType=AD_HOC`）
  4. 同じSQLを`POST /api/saved-queries/{connectionId}`で保存し、再度実行後に`queryType=SAVED`・`savedQueryName`が正しく解決されることを確認
- **Expected Results**: SQL生成・実行・履歴記録が一貫して連携し、保存クエリ経由の実行では種別・名前が正しく解決される

### Scenario 4: 各ユニットの操作 → 監査ログ閲覧（UNIT-02〜09 → UNIT-09）

- **Description**: UNIT-02〜08の各操作（ログイン、接続登録、権限変更、クエリ実行等）が監査ログに記録され、管理者専用の監査ログ閲覧画面から絞込・参照できることを確認する
- **Test Steps**:
  1. Scenario 1〜3で発生する各種操作後、`GET /api/admin/audit-log`で該当イベント（`LOGIN`, `CONNECTION_REGISTERED`, `PERMISSION_CHANGED`, `QUERY_EXECUTED`等）が記録されていることを確認
  2. `eventType`・`userId`・`connectionId`・`resultStatus`の各絞込条件で正しく絞り込めることを確認
  3. 一般ユーザのトークンで同エンドポイントにアクセスし、403で拒否されることを確認（BR-AUDITVIEW-03）
- **Expected Results**: 全操作が対応する`AuditEventType`で記録され、絞込が正しく機能し、管理者以外はアクセスできない

### Scenario 5: 接続削除後のプレースホルダー表示（UNIT-03 → UNIT-08/UNIT-09）

- **Description**: RDBMS接続を削除した後も、既存の実行履歴・監査ログは記録の不変性の原則により閲覧可能で、削除済み接続として適切に表示されることを確認する
- **Test Steps**:
  1. Scenario 2〜4で使用した接続を`DELETE /api/admin/rdbms-connections/{id}`で削除
  2. `GET /api/query-history/{connectionId}`（UNIT-08）・`GET /api/admin/audit-log?connectionId={id}`（UNIT-09）を再度呼び出す
- **Expected Results**: 履歴・監査ログのレコード自体は引き続き閲覧可能で、接続の表示名が「(削除済み接続)」のプレースホルダーになる

## Run Integration Tests

上記シナリオはcurl等でのAPI呼び出しによる手動検証を基本とする（本プロジェクトはE2Eテストフレームワーク（Playwright等）を導入していない。導入検討はBacklogに記載済み、`aidlc-docs/aidlc-state.md`参照）。

### 1. 実行ログの確認

- **ログ出力先**: アプリケーション標準出力（起動時に指定したターミナル、またはリダイレクト先ファイル）
- **DEBUGログが必要な場合**: `--MM_LOGGING_LEVEL_APP=DEBUG`を起動時オプションに追加（`TRACE`は認証情報がログに残るため通常は使用しない、`backend/README.md`参照）

### 2. Cleanup

```bash
# アプリケーションプロセスを停止
kill <PID>

# devenvコンテナは他の検証でも再利用するため、明示的な指示がない限り停止しない
# 停止する場合:
# cd devenv && docker compose down

# テスト用の内部DBファイルを削除
rm -rf /tmp/mm-integration-test-data
```
