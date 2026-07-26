/*
 * Copyright 2026 agwlvssainokuni
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cherry.mastermeister.queryhistory;

import cherry.mastermeister.query.entity.QueryExecutionRecord;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;

/**
 * nfr-design-patterns.md §2.1。QueryExecutionRecordの動的絞込条件を組み立てる
 * 静的ファクトリメソッド集（プロジェクト内初のSpecification API採用）。
 */
public final class QueryHistorySpecifications {

    private QueryHistorySpecifications() {
    }

    public static Specification<QueryExecutionRecord> connectionIdEquals(Long connectionId) {
        return (root, query, cb) -> cb.equal(root.get("connectionId"), connectionId);
    }

    public static Specification<QueryExecutionRecord> executedByEquals(Long executedBy) {
        return (root, query, cb) -> cb.equal(root.get("executedBy"), executedBy);
    }

    public static Specification<QueryExecutionRecord> executedAtFrom(Instant from) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("executedAt"), from);
    }

    public static Specification<QueryExecutionRecord> executedAtTo(Instant to) {
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("executedAt"), to);
    }

    public static Specification<QueryExecutionRecord> schemaNameEquals(String schemaName) {
        return (root, query, cb) -> cb.equal(root.get("schemaName"), schemaName);
    }

    public static Specification<QueryExecutionRecord> sqlContains(String keyword) {
        return (root, query, cb) -> cb.like(root.get("sql"), "%" + keyword + "%");
    }
}
