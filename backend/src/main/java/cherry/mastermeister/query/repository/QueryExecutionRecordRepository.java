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

package cherry.mastermeister.query.repository;

import cherry.mastermeister.query.entity.QueryExecutionRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * UNIT-08 logical-components.md §2。JpaSpecificationExecutorはQueryHistoryServiceの
 * 動的絞込クエリ（QueryHistorySpecifications）で使用する。DISTINCT取得系メソッドは
 * 接続一覧・スキーマ名一覧の実行者スコープ別バリエーション（BR-QUERYHISTORY-10・11）。
 */
public interface QueryExecutionRecordRepository
        extends JpaRepository<QueryExecutionRecord, Long>, JpaSpecificationExecutor<QueryExecutionRecord> {

    @Query("select distinct r.connectionId from QueryExecutionRecord r where r.executedBy = :executedBy")
    List<Long> findDistinctConnectionIdByExecutedBy(@Param("executedBy") Long executedBy);

    @Query("select distinct r.connectionId from QueryExecutionRecord r")
    List<Long> findDistinctConnectionId();

    @Query("select distinct r.schemaName from QueryExecutionRecord r "
            + "where r.connectionId = :connectionId and r.executedBy = :executedBy")
    List<String> findDistinctSchemaNameByConnectionIdAndExecutedBy(@Param("connectionId") Long connectionId,
                                                                    @Param("executedBy") Long executedBy);

    @Query("select distinct r.schemaName from QueryExecutionRecord r where r.connectionId = :connectionId")
    List<String> findDistinctSchemaNameByConnectionId(@Param("connectionId") Long connectionId);
}
