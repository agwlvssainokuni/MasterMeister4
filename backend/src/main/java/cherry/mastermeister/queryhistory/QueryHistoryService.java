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
import cherry.mastermeister.query.entity.SavedQuery;
import cherry.mastermeister.query.repository.QueryExecutionRecordRepository;
import cherry.mastermeister.query.repository.SavedQueryRepository;
import cherry.mastermeister.queryhistory.dto.QueryHistoryConnectionResponse;
import cherry.mastermeister.queryhistory.dto.QueryHistoryRecordResponse;
import cherry.mastermeister.queryhistory.dto.QueryHistorySearchCriteria;
import cherry.mastermeister.queryhistory.dto.QueryType;
import cherry.mastermeister.rdbmsconnection.entity.RdbmsConnection;
import cherry.mastermeister.rdbmsconnection.repository.RdbmsConnectionRepository;
import cherry.mastermeister.registration.entity.User;
import cherry.mastermeister.registration.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static cherry.mastermeister.queryhistory.QueryHistorySpecifications.connectionIdEquals;
import static cherry.mastermeister.queryhistory.QueryHistorySpecifications.executedAtFrom;
import static cherry.mastermeister.queryhistory.QueryHistorySpecifications.executedAtTo;
import static cherry.mastermeister.queryhistory.QueryHistorySpecifications.executedByEquals;
import static cherry.mastermeister.queryhistory.QueryHistorySpecifications.schemaNameEquals;
import static cherry.mastermeister.queryhistory.QueryHistorySpecifications.sqlContains;

/**
 * COMP-17。business-logic-model.md §2〜6。絞込・ページング・名前解決の3責務を担う
 * （nfr-design-plan.md Q2=A）。呼び出し元（QueryHistoryController）が実行者スコープの
 * ロール判定を済ませたexecutedByFilter（nullなら全ユーザ対象）を受け取る。本クラス自体は
 * ロール判定ロジックを持たない（nfr-design-patterns.md §1.2）。
 */
@Service
public class QueryHistoryService {

    private static final String DELETED_CONNECTION_PLACEHOLDER = "(削除済み接続)";
    private static final String DELETED_SAVED_QUERY_PLACEHOLDER = "(削除済み)";
    private static final String UNKNOWN_USER_PLACEHOLDER = "(不明なユーザ)";

    private final QueryExecutionRecordRepository queryExecutionRecordRepository;
    private final SavedQueryRepository savedQueryRepository;
    private final UserRepository userRepository;
    private final RdbmsConnectionRepository rdbmsConnectionRepository;

    public QueryHistoryService(QueryExecutionRecordRepository queryExecutionRecordRepository,
                                SavedQueryRepository savedQueryRepository, UserRepository userRepository,
                                RdbmsConnectionRepository rdbmsConnectionRepository) {
        this.queryExecutionRecordRepository = queryExecutionRecordRepository;
        this.savedQueryRepository = savedQueryRepository;
        this.userRepository = userRepository;
        this.rdbmsConnectionRepository = rdbmsConnectionRepository;
    }

    /**
     * BR-QUERYHISTORY-11。business-logic-model.md §3-1。
     */
    public List<QueryHistoryConnectionResponse> listConnections(Long executedByFilter) {
        List<Long> connectionIds = executedByFilter != null
                ? queryExecutionRecordRepository.findDistinctConnectionIdByExecutedBy(executedByFilter)
                : queryExecutionRecordRepository.findDistinctConnectionId();
        Map<Long, RdbmsConnection> connections = rdbmsConnectionRepository.findAllById(connectionIds).stream()
                .collect(Collectors.toMap(RdbmsConnection::getId, c -> c));
        return connectionIds.stream()
                .map(id -> new QueryHistoryConnectionResponse(id,
                        connections.containsKey(id) ? connections.get(id).getDisplayName()
                                : DELETED_CONNECTION_PLACEHOLDER))
                .toList();
    }

    /**
     * BR-QUERYHISTORY-10。承認前レビューで実行者スコープによるフィルタを追加（情報漏洩対策）。
     */
    public List<String> listSchemas(Long connectionId, Long executedByFilter) {
        return executedByFilter != null
                ? queryExecutionRecordRepository.findDistinctSchemaNameByConnectionIdAndExecutedBy(connectionId,
                        executedByFilter)
                : queryExecutionRecordRepository.findDistinctSchemaNameByConnectionId(connectionId);
    }

    /**
     * business-logic-model.md §2・§5〜6。BR-QUERYHISTORY-09（絞込条件はすべてAND結合）。
     */
    public Page<QueryHistoryRecordResponse> listHistory(Long connectionId, Long executedByFilter,
                                                         QueryHistorySearchCriteria criteria, Pageable pageable) {
        Specification<QueryExecutionRecord> spec = Specification.where(connectionIdEquals(connectionId));
        if (executedByFilter != null) {
            spec = spec.and(executedByEquals(executedByFilter));
        }
        if (criteria.executedAtFrom() != null) {
            spec = spec.and(executedAtFrom(criteria.executedAtFrom()));
        }
        if (criteria.executedAtTo() != null) {
            spec = spec.and(executedAtTo(criteria.executedAtTo()));
        }
        if (criteria.schemaName() != null) {
            spec = spec.and(schemaNameEquals(criteria.schemaName()));
        }
        if (criteria.sqlKeyword() != null && !criteria.sqlKeyword().isBlank()) {
            spec = spec.and(sqlContains(criteria.sqlKeyword()));
        }

        Page<QueryExecutionRecord> page = queryExecutionRecordRepository.findAll(spec, pageable);

        Set<Long> savedQueryIds = page.getContent().stream()
                .map(QueryExecutionRecord::getSavedQueryId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, SavedQuery> savedQueries = savedQueryRepository.findAllByIdIn(savedQueryIds).stream()
                .collect(Collectors.toMap(SavedQuery::getId, sq -> sq));

        Set<Long> executedByIds = page.getContent().stream()
                .map(QueryExecutionRecord::getExecutedBy)
                .collect(Collectors.toSet());
        Map<Long, User> users = userRepository.findAllById(executedByIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        return page.map(record -> toResponse(record, savedQueries, users));
    }

    private QueryHistoryRecordResponse toResponse(QueryExecutionRecord record, Map<Long, SavedQuery> savedQueries,
                                                   Map<Long, User> users) {
        User user = users.get(record.getExecutedBy());
        String executorDisplayName = user != null ? user.getFullName() : UNKNOWN_USER_PLACEHOLDER;

        Long savedQueryId = record.getSavedQueryId();
        String savedQueryName = null;
        QueryType queryType = QueryType.AD_HOC;
        if (savedQueryId != null) {
            queryType = QueryType.SAVED;
            SavedQuery savedQuery = savedQueries.get(savedQueryId);
            savedQueryName = savedQuery != null ? savedQuery.getName() : DELETED_SAVED_QUERY_PLACEHOLDER;
        }

        return new QueryHistoryRecordResponse(record.getId(), record.getExecutedBy(), executorDisplayName,
                record.getConnectionId(), record.getSchemaName(), record.getSql(), savedQueryId, savedQueryName,
                queryType, record.getRowCount(), record.getDurationMillis(), record.getExecutedAt());
    }
}
