# UNIT-05 マスタメンテナンス - Logical Components

nfr-design-patterns.mdで確定した実装パターンを、具体的な論理コンポーネント（クラス・設定・DTO）に落とし込む。パッケージは`cherry.mastermeister.masterdata`（`unit-of-work.md`のユニット→パッケージ対応表のとおり）。

---

## 1. マスタデータドメイン（`cherry.mastermeister.masterdata`）

### MasterDataController（COMP-13の一部、Q8=A）
単一Controllerに全エンドポイントをまとめる。
- `GET /api/master-data/connections` — アクセス可能な接続一覧（BR-MASTER-13）
- `GET /api/master-data/connections/{connectionId}/tables` — アクセス可能なテーブル/ビュー一覧（BR-MASTER-01〜02）
- `GET /api/master-data/connections/{connectionId}/tables/{schemaName}/{tableName}/records` — レコード一覧（ページング・絞込・SQL手入力。BR-MASTER-04〜05, 10, 14）
- `POST /api/master-data/connections/{connectionId}/tables/{schemaName}/{tableName}/records/batch` — 一括反映（BR-MASTER-06〜09）
- 全エンドポイントとも、新規SecurityFilterChainルール（nfr-design-patterns.md §3.2）により認証済みユーザ（ロール不問）がアクセス可能

### MasterDataService（COMP-13）
- business-logic-model.md §1〜2の接続/テーブル一覧取得フローを実装。`EffectivePermissionResolver`（UNIT-04）を直接呼び出し、`resolvePrimary`/`canCreate`/`canDelete`の結果からBR-MASTER-01・BR-MASTER-13の判定を行う
- テーブル一覧取得時は`SchemaIntrospectionService`（UNIT-03）のスキーマ情報を参照する

### RecordQueryService（COMP-13の一部）
- business-logic-model.md §3のレコード一覧取得フローを実装
- `RdbmsConnectionService.getDataSource(connectionId)`と`NamedParameterJdbcTemplate`で、対象テーブルへの動的SELECT文（絞込・ページング）を組み立てて実行する（nfr-design-patterns.md §2.1）
- 表示対象カラムの絞り込み（実効主権限`READ`以上のみ）・編集可否（`UPDATE`のみ）の判定にも`EffectivePermissionResolver`を利用する
- 取得結果件数が`AppProperties`の閾値以上の場合、`MASTER_DATA_BULK_ACCESSED`を記録する

### RecordBatchService（COMP-13の一部）
- business-logic-model.md §4の一括反映フローを実装
- リクエストごとに`new DataSourceTransactionManager(dataSource)`を生成し、`TransactionTemplate`でトランザクション制御する（nfr-design-patterns.md §1.1, §2.1）
- 権限チェック（事前検証）→トランザクション内で個別SQL実行→成功時コミット・`MASTER_DATA_BATCH_APPLIED`記録／失敗時ロールバック・失敗理由返却、の順で処理する
- バッチ合計件数が`AppProperties`の上限を超える場合、DBアクセス前に拒否する

### RawQueryConditionValidator（COMP-13の一部）
- nfr-design-patterns.md §3.1のSQL構文検証・パラメータ化を実装
- JSqlParserでWHERE/ORDER BY句をダミーSELECT文経由でパースし、`ExpressionVisitor`で許可構文要素のみか検証する
- 検証失敗時は`InvalidQueryConditionException`を送出する。検証成功時はパラメータ化された条件（SQL文字列＋バインドパラメータのマップ）を返す

### ColumnDataTypeMapper（COMP-13の一部、Q4=A）
- UNIT-03の`SchemaColumn`が保持するJDBC型情報（`java.sql.Types`相当）から、`ColumnDataTypeCategory`（`NUMERIC`/`DATETIME`/`STRING`/`BOOLEAN`）への変換を行う（BR-MASTER-05のフィルタ演算子決定に使用）
- UNIT-03のエンティティ自体には変更を加えない（モジュール境界を`masterdata`パッケージ内に閉じる）

### InvalidQueryConditionException（新規例外）
- `GlobalExceptionHandler`（UNIT-02）に`@ExceptionHandler`を追加し、`VALIDATION_ERROR`（400）にマッピングする（nfr-design-patterns.md §1.2）

### DTO設計
- `AccessibleConnectionResponse`（`connectionId`, `displayName`）
- `AccessibleTableResponse`（`schemaName`, `tableName`, `tableType`, `creatable`, `deletable`）
- `RecordPageResponse`（`columns: RecordColumnResponse[]`, `rows`, `page`, `pageSize`, `totalCount`, `creatable`, `deletable`）
- `RecordColumnResponse`（`columnName`, `dataTypeCategory`, `primaryKey`, `editable`）
- `RecordFilterRequest`（`columnName`, `operator`, `value`, `valueTo`）
- `BatchOperationRequest`/`BatchOperationItemRequest`（domain-entities.md §7のとおり）
- `BatchOperationResultResponse`/`BatchOperationItemResultResponse`（domain-entities.md §8のとおり）

---

## 2. 依存関係の追加

`backend/build.gradle.kts`に以下を追加する。
- `implementation("com.github.jsqlparser:jsqlparser:...")`（tech-stack-decisions.md §1、バージョンはCode Generation時点の最新安定版を使用）

---

## 3. 設定（`AppProperties`拡張、Q6=A）

`application.yml`に以下を追加する。

```yaml
mm:
  app:
    masterdata:
      batch-max-size: 1000
    audit:
      bulk-access-threshold: 100
```

`AppProperties`に対応するネストプロパティ（`masterdata.batchMaxSize`, `audit.bulkAccessThreshold`）を追加し、`RecordBatchService`/`RecordQueryService`から参照する。

---

## 4. Spring Security設定の変更（nfr-design-patterns.md §3.2）

UNIT-02で確立済みのSecurityFilterChain設定（`cherry.mastermeister.common.config`配下）に、`/api/master-data/**`へのルールを追加する。既存の`/api/admin/**`（`ADMIN`ロール必須）ルールとは別に、`/api/master-data/**`は認証済み（`hasRole`不問、`authenticated()`のみ）とするルールを、既存ルールより前または適切な優先順位で追加する。

---

## 5. 監査ログ連携

`AuditEventPublisher`（UNIT-02で新設済み、`cherry.mastermeister.audit`）を通じて、domain-entities.md §9で定義した2種のイベント（`MASTER_DATA_BULK_ACCESSED`, `MASTER_DATA_BATCH_APPLIED`）を発行する。
