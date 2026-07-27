# Security Test Instructions

## Purpose

requirements.mdのSecurity Baseline拡張（各ユニットのNFR Requirements段階で全15ルールを評価済み）に基づき、脆弱性スキャン・認証認可・入力検証の観点で検証手順を定める。

## 1. 依存関係の脆弱性スキャン（NFR-4.4、SECURITY-10）

```bash
./gradlew :backend:dependencyCheckAnalyze
```

- **前提**: NVD APIキーが必要（`--info.nvd.api.key`または環境変数）。本プロジェクトの開発期間中はNVD APIキー未設定のため、全ユニットのCode Generation時に実施を見送ってきた
- **CI連携**: `.github/workflows/ci.yml`の`dependency-check`ジョブが、リポジトリシークレット`NVD_API_KEY`設定時のみ本タスクを実行する（未設定時は該当ステップをスキップ、`continue-on-error: true`）
- **フロントエンド側**: `npm audit`（`frontend/`ディレクトリで実行）で既知の脆弱性を確認する

## 2. 認証・認可のテスト

### 2.1 JWT認証

- 未認証リクエスト（`Authorization`ヘッダなし）が保護対象エンドポイントで401を返すことを確認する（各ユニットのControllerテストで実施済み、`@WebMvcTest`＋`SecurityConfig`実インポート）
- 有効期限切れ・改ざんされたJWTでのアクセスが拒否されることを確認する（UNIT-02の認証基盤テストで実施済み）
- リフレッシュトークンのローテーション・再利用検知（STORY-3.2）が機能することを確認する

### 2.2 ロールベースアクセス制御（管理者専用エンドポイント）

- `/api/admin/**`配下の全エンドポイントが、一般ユーザ（`ROLE_USER`）からのアクセスで403を返すことを確認する（対象: UNIT-02のユーザ管理、UNIT-03のRDBMS接続管理、UNIT-04のグループ・権限管理、UNIT-09の監査ログ閲覧）
- 実行コマンド例:
  ```bash
  curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/api/admin/audit-log \
    -H "Authorization: Bearer <一般ユーザのトークン>"
  # 期待値: 403
  ```

### 2.3 実効権限（マスタデータ・クエリ系）

- STORY-2.4のPBT対象（PBT-01, PBT-03）で主権限・補助権限の合成ロジックを網羅的に検証済み（`EffectivePermissionResolver`）
- 実行者スコープのフェイルクローズ（UNIT-08クエリ履歴: 一般ユーザは常に自分の履歴のみ）を、一般ユーザのトークンで`executedByScope=ALL`を明示指定しても自分の履歴のみが返ることで確認する

## 3. 入力検証のテスト

- 各ユニットのController層で、ページサイズ上限超過・日時範囲の相関違反（開始>終了）等の不正入力に対し400エラーを返すことを確認する（`QueryHistoryInvalidParameterException`、`AuditLogInvalidParameterException`等）
- SQLインジェクション対策: クエリビルダー（UNIT-07）・クエリ実行（UNIT-06）はJSqlParserのASTオブジェクトモデル、JPA Criteria API（Specification）のパラメータバインドを使用し、文字列連結によるSQL組み立てを行わない（構造的な対策、各ユニットのNFR Design段階で確立済み）
- パスワード強度・既知漏洩パスワードチェック（SECURITY-12）: 単純なパスワードでの登録完了が`PASSWORD_COMPROMISED`で拒否されることを確認する（UNIT-02実装済み、UNIT-09実機検証時にも再確認済み）

## 4. 監査ログの整合性

- Integration Test Instructions（Scenario 4）で確認したとおり、セキュリティ上重要な操作（ログイン、権限変更、接続の登録/更新/削除等）が`AuditLogEntry`に記録され、UNIT-09の閲覧画面（管理者専用）から確認できることを検証する

## 本プロジェクトでは実施しない項目

- ペネトレーションテスト（外部専門家によるものを含む、社内10名規模のツールという前提のためスコープ外、requirements.md NFR-4.1参照）
- 多要素認証（MFA）関連のテスト（NFR-4.1で文書化された適用除外事項）
