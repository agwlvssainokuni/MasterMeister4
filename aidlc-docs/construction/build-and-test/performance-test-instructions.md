# Performance Test Instructions

## Purpose

requirements.md NFR-1.1により、本プロジェクトの想定同時利用者数は約10名規模の社内ツールであり、大規模な負荷テスト・スケーラビリティ検証は要件として求められていない（各ユニットのNFR Requirements段階で一貫して「スケーラビリティ要件はN/A」と判定済み）。本ドキュメントは、本格的な負荷試験ツール（JMeter/k6等）の導入を前提とせず、想定規模に見合った軽量な確認手順を定める。

## Performance Requirements

- **同時利用者数**: 約10名（NFR-1.1）
- **明示的なレスポンスタイム・スループット目標値**: requirements.mdに定量的な数値目標の記載なし（規模の小ささから明示的な目標値を設定していない）
- **エラー率**: 明示的な数値目標なし（通常運用でエラーが発生しないことを確認する程度）

## 軽量な確認手順

### 1. アプリケーション起動時間の確認

```bash
java -jar backend/build/libs/mastermeister-*.war --MM_APP_DATASOURCE_PATH=/tmp/perf-check/mastermeister
```

- **確認内容**: ログの`Started MasterMeisterApplication in <X> seconds`を確認する
- **目安**: 開発環境（ローカルマシン）で数秒〜10秒程度であれば問題ない水準とする（明示的な合格基準はなし）

### 2. 主要APIの単発レスポンスタイムの目視確認

```bash
time curl -s http://localhost:8080/api/admin/audit-log -H "Authorization: Bearer <token>" > /dev/null
```

- **対象**: マスタデータ一覧取得（UNIT-05）、クエリ実行（UNIT-06）、クエリ履歴一覧（UNIT-08）、監査ログ一覧（UNIT-09）等、絞込・ページングを伴う一覧系API
- **確認内容**: 想定データ量（開発・検証環境の実機E2Eで扱った程度のレコード数）で、体感上遅延が問題にならないことを目視確認する

### 3. インデックス設計の妥当性確認（実装済み事項の再確認）

各ユニットのNFR Requirements/NFR Design段階で、主要な絞込・ソートパターンに対する複合インデックスを設計・追加済みである。パフォーマンステストとしては、これらのインデックスが実際のクエリ実行計画で使用されているかを確認する。

```bash
# 例（PostgreSQL、devenv経由で対象RDBMSに接続して確認する場合）
EXPLAIN ANALYZE SELECT * FROM query_execution_record
  WHERE connection_id = 1 ORDER BY executed_at DESC LIMIT 50;
EXPLAIN ANALYZE SELECT * FROM audit_log_entry
  WHERE connection_id = 1 ORDER BY occurred_at DESC LIMIT 50;
```

- **確認内容**: `Index Scan`（または相当する実行計画）が使われ、`Seq Scan`（全表走査）になっていないことを確認する

## 本プロジェクトでは実施しない項目

- 大規模データセットでの負荷試験（JMeter/k6等の専用ツールを用いた多重リクエストシミュレーション）
- 長時間の耐久試験（ソークテスト）
- スケールアウト・水平スケーリングの検証（NFR-1.1の規模前提により対象外）

将来的にビジネスクリティカルな用途に発展し利用規模が拡大する場合は、requirements.mdの前提（NFR-1.1）自体を見直したうえで、本格的な負荷試験の導入を別途検討する（`aidlc-docs/aidlc-state.md`のBacklogにも関連事項を記載済み）。
