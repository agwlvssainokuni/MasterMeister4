-- UNIT-08 クエリ履歴。logical-components.md §7、nfr-requirements.md Q2=A。
-- 本ユニットの主要クエリパターン（connection_id指定＋executed_at降順ソート）に最適化する
-- 複合インデックス。既存のexecuted_by/executed_at/saved_query_id単独インデックスは変更しない
-- （他の絞込条件との組み合わせにも引き続き寄与するため）。
CREATE INDEX idx_query_execution_record_connection_executed_at
    ON query_execution_record (connection_id, executed_at);
