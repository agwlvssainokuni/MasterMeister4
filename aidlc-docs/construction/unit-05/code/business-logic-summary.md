# UNIT-05 マスタメンテナンス - Business Logic Summary

Code Generation計画Step 5〜7の実施結果。

## 作成したサービス

| コンポーネント | パッケージ | 責務 |
|---|---|---|
| `MasterDataService` | `cherry.mastermeister.masterdata` | アクセス可能な接続一覧・テーブル/ビュー一覧の取得（BR-MASTER-01〜03・13）、レコード一覧取得・一括反映のオーケストレーション（`RecordQueryService`/`RecordBatchService`への委譲）、監査イベント（`MASTER_DATA_BULK_ACCESSED`/`MASTER_DATA_BATCH_APPLIED`）の発行判断 |

新規例外（Step 3で前倒し作成）: `InvalidQueryConditionException`, `BatchSizeExceededException`, `MasterDataTableNotAccessibleException`（対象テーブル/ビューが存在しない場合と非公開の場合を区別しない404、フェイルクローズな設計判断）。

## 主要な設計判断

- **BR-MASTER-13（アクセス可能な接続の判定）**: 「スキーマ／テーブル／カラムいずれかの階層で実効主権限READ以上」という要件を、既存の`resolvePrimary`の階層フォールバック（カラム→テーブル→スキーマ）の性質を利用し、「接続内の少なくとも1テーブルがBR-MASTER-01の可視条件を満たすか」という単純化した判定に落とし込んだ。`resolvePrimary(schema, table, null)`自体がスキーマ階層への設定へフォールバックするため、テーブル単位の可視判定だけでスキーマ階層の設定も間接的に評価される。
- **監査イベント発行の集約**: `RecordQueryService`/`RecordBatchService`は純粋なデータアクセス層とし、閾値比較や成功時のみの発行判断は`MasterDataService`に集約した。`MASTER_DATA_BULK_ACCESSED`の「結果件数」は、実際にレスポンスへ含めた行数（`RecordPage.rows().size()`、ページングされた実件数）を用いる。
- **CREATE/UPDATE時の値設定可能カラム**: 明文化されたルールはなかったが、値を設定できるカラムは実効主権限`UPDATE`（`editable=true`）のものに限定した（`RecordBatchService.validateColumnValuesEditable`）。BR-ACCESS-06により`canCreate()`が真となる前提として主キー列は既に`UPDATE`権限を持つため、この制約は主キー値の指定を妨げない。

## テスト結果

- `MasterDataServiceTest`: 11件成功（Mockito。BR-MASTER-01の可視判定、BR-MASTER-02のVIEW常時読み取り専用化、BR-MASTER-13の接続可視判定、`SchemaNotImportedException`/`MasterDataTableNotAccessibleException`/`BatchSizeExceededException`の送出条件）
- `MasterDataServiceColumnVisibilityPropertyTest`: 1プロパティ成功（jqwik、business-logic-model.md §7.3。実効主権限NONEのカラムがいかなる組み合わせでもレスポンスに含まれないことを、任意のカラム×権限の組み合わせで検証）
- `RawQueryConditionValidatorPropertyTest`: 3プロパティ成功（jqwik、§7.1。安全性の不変条件（数値・文字列リテラルとも常にバインドパラメータ経由）、拒否の健全性（サブクエリ・関数呼び出し・コメント記号・複数ステートメントの組み合わせを網羅的に生成し拒否を確認））
- `RecordBatchServicePropertyTest`: 2プロパティ成功（jqwik、§7.2。H2実DBに対し、制約違反を含む任意のID組み合わせで常に全件ロールバックされること、全件有効な場合は常に全件反映されることを確認）

`./gradlew :backend:test`実行結果: 全256件成功（UNIT-01〜04の既存テスト含む、リグレッションなし）。
