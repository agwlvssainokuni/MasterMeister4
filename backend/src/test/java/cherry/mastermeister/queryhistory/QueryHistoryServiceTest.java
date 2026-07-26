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
import cherry.mastermeister.query.entity.Visibility;
import cherry.mastermeister.query.repository.QueryExecutionRecordRepository;
import cherry.mastermeister.query.repository.SavedQueryRepository;
import cherry.mastermeister.queryhistory.dto.QueryHistoryConnectionResponse;
import cherry.mastermeister.queryhistory.dto.QueryHistoryRecordResponse;
import cherry.mastermeister.queryhistory.dto.QueryHistorySearchCriteria;
import cherry.mastermeister.queryhistory.dto.QueryType;
import cherry.mastermeister.rdbmsconnection.entity.DbType;
import cherry.mastermeister.rdbmsconnection.entity.RdbmsConnection;
import cherry.mastermeister.rdbmsconnection.repository.RdbmsConnectionRepository;
import cherry.mastermeister.registration.entity.Language;
import cherry.mastermeister.registration.entity.Role;
import cherry.mastermeister.registration.entity.User;
import cherry.mastermeister.registration.entity.UserStatus;
import cherry.mastermeister.registration.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * COMP-17。business-logic-model.md §2〜6。BR-QUERYHISTORY-01〜11。
 */
class QueryHistoryServiceTest {

    private static final Long CONNECTION_ID = 100L;
    private static final Long USER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;

    private QueryExecutionRecordRepository queryExecutionRecordRepository;
    private SavedQueryRepository savedQueryRepository;
    private UserRepository userRepository;
    private RdbmsConnectionRepository rdbmsConnectionRepository;
    private QueryHistoryService service;

    @BeforeEach
    void setUp() {
        queryExecutionRecordRepository = mock(QueryExecutionRecordRepository.class);
        savedQueryRepository = mock(SavedQueryRepository.class);
        userRepository = mock(UserRepository.class);
        rdbmsConnectionRepository = mock(RdbmsConnectionRepository.class);
        service = new QueryHistoryService(queryExecutionRecordRepository, savedQueryRepository, userRepository,
                rdbmsConnectionRepository);
    }

    @Test
    void listConnections_withExecutedByFilter_usesScopedQuery() {
        when(queryExecutionRecordRepository.findDistinctConnectionIdByExecutedBy(USER_ID))
                .thenReturn(List.of(CONNECTION_ID));
        when(rdbmsConnectionRepository.findAllById(List.of(CONNECTION_ID)))
                .thenReturn(List.of(rdbmsConnection(CONNECTION_ID, "接続A")));

        List<QueryHistoryConnectionResponse> result = service.listConnections(USER_ID);

        assertThat(result).containsExactly(new QueryHistoryConnectionResponse(CONNECTION_ID, "接続A"));
    }

    @Test
    void listConnections_withoutExecutedByFilter_usesAllUsersQuery() {
        when(queryExecutionRecordRepository.findDistinctConnectionId()).thenReturn(List.of(CONNECTION_ID));
        when(rdbmsConnectionRepository.findAllById(List.of(CONNECTION_ID)))
                .thenReturn(List.of(rdbmsConnection(CONNECTION_ID, "接続A")));

        List<QueryHistoryConnectionResponse> result = service.listConnections(null);

        assertThat(result).containsExactly(new QueryHistoryConnectionResponse(CONNECTION_ID, "接続A"));
    }

    @Test
    void listConnections_showsPlaceholderForDeletedConnection() {
        when(queryExecutionRecordRepository.findDistinctConnectionIdByExecutedBy(USER_ID))
                .thenReturn(List.of(CONNECTION_ID));
        when(rdbmsConnectionRepository.findAllById(List.of(CONNECTION_ID))).thenReturn(List.of());

        List<QueryHistoryConnectionResponse> result = service.listConnections(USER_ID);

        assertThat(result).containsExactly(new QueryHistoryConnectionResponse(CONNECTION_ID, "(削除済み接続)"));
    }

    @Test
    void listSchemas_withExecutedByFilter_usesScopedQuery() {
        when(queryExecutionRecordRepository.findDistinctSchemaNameByConnectionIdAndExecutedBy(CONNECTION_ID, USER_ID))
                .thenReturn(List.of("public"));

        assertThat(service.listSchemas(CONNECTION_ID, USER_ID)).containsExactly("public");
    }

    @Test
    void listSchemas_withoutExecutedByFilter_usesAllUsersQuery() {
        when(queryExecutionRecordRepository.findDistinctSchemaNameByConnectionId(CONNECTION_ID))
                .thenReturn(List.of("public", "sales"));

        assertThat(service.listSchemas(CONNECTION_ID, null)).containsExactly("public", "sales");
    }

    @Test
    void listHistory_resolvesSavedQueryNameAndExecutorDisplayName() {
        QueryExecutionRecord record = queryExecutionRecord(1L, USER_ID, CONNECTION_ID, "public", "SELECT 1", 10L);
        Pageable pageable = PageRequest.of(0, 20);
        mockFindAll(record, pageable);
        when(savedQueryRepository.findAllByIdIn(any())).thenReturn(List.of(savedQuery(10L, "マイクエリ")));
        when(userRepository.findAllById(any())).thenReturn(List.of(user(USER_ID, "山田太郎")));

        Page<QueryHistoryRecordResponse> result = service.listHistory(CONNECTION_ID, USER_ID, emptyCriteria(),
                pageable);

        QueryHistoryRecordResponse response = result.getContent().get(0);
        assertThat(response.savedQueryName()).isEqualTo("マイクエリ");
        assertThat(response.queryType()).isEqualTo(QueryType.SAVED);
        assertThat(response.executorDisplayName()).isEqualTo("山田太郎");
    }

    @Test
    void listHistory_adHocQueryHasNullSavedQueryName() {
        QueryExecutionRecord record = queryExecutionRecord(2L, USER_ID, CONNECTION_ID, "public", "SELECT 1", null);
        Pageable pageable = PageRequest.of(0, 20);
        mockFindAll(record, pageable);
        when(savedQueryRepository.findAllByIdIn(any())).thenReturn(List.of());
        when(userRepository.findAllById(any())).thenReturn(List.of(user(USER_ID, "山田太郎")));

        Page<QueryHistoryRecordResponse> result = service.listHistory(CONNECTION_ID, USER_ID, emptyCriteria(),
                pageable);

        QueryHistoryRecordResponse response = result.getContent().get(0);
        assertThat(response.savedQueryName()).isNull();
        assertThat(response.queryType()).isEqualTo(QueryType.AD_HOC);
    }

    @Test
    void listHistory_showsPlaceholderForDeletedSavedQuery() {
        QueryExecutionRecord record = queryExecutionRecord(3L, USER_ID, CONNECTION_ID, "public", "SELECT 1", 99L);
        Pageable pageable = PageRequest.of(0, 20);
        mockFindAll(record, pageable);
        when(savedQueryRepository.findAllByIdIn(any())).thenReturn(List.of());
        when(userRepository.findAllById(any())).thenReturn(List.of(user(USER_ID, "山田太郎")));

        Page<QueryHistoryRecordResponse> result = service.listHistory(CONNECTION_ID, USER_ID, emptyCriteria(),
                pageable);

        assertThat(result.getContent().get(0).savedQueryName()).isEqualTo("(削除済み)");
    }

    @Test
    void listHistory_showsPlaceholderForUnknownExecutor() {
        QueryExecutionRecord record = queryExecutionRecord(4L, OTHER_USER_ID, CONNECTION_ID, "public", "SELECT 1",
                null);
        Pageable pageable = PageRequest.of(0, 20);
        mockFindAll(record, pageable);
        when(savedQueryRepository.findAllByIdIn(any())).thenReturn(List.of());
        when(userRepository.findAllById(any())).thenReturn(List.of());

        Page<QueryHistoryRecordResponse> result = service.listHistory(CONNECTION_ID, null, emptyCriteria(),
                pageable);

        assertThat(result.getContent().get(0).executorDisplayName()).isEqualTo("(不明なユーザ)");
    }

    private void mockFindAll(QueryExecutionRecord record, Pageable pageable) {
        when(queryExecutionRecordRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(record), pageable, 1));
    }

    private static QueryHistorySearchCriteria emptyCriteria() {
        return new QueryHistorySearchCriteria(null, null, null, null);
    }

    private static QueryExecutionRecord queryExecutionRecord(Long id, Long executedBy, Long connectionId,
                                                               String schemaName, String sql, Long savedQueryId) {
        QueryExecutionRecord record = new QueryExecutionRecord(executedBy, connectionId, schemaName, sql, null,
                savedQueryId, 3L, 5L, Instant.now());
        setField(record, "id", id);
        return record;
    }

    private static SavedQuery savedQuery(Long id, String name) {
        SavedQuery savedQuery = new SavedQuery(CONNECTION_ID, name, "SELECT 1", Visibility.PRIVATE, USER_ID,
                Instant.now());
        setField(savedQuery, "id", id);
        return savedQuery;
    }

    private static User user(Long id, String fullName) {
        User user = new User("user" + id + "@example.com", "hash", fullName, Language.ja, UserStatus.APPROVED,
                Role.USER, Instant.now(), Instant.now(), null);
        setField(user, "id", id);
        return user;
    }

    private static RdbmsConnection rdbmsConnection(Long id, String displayName) {
        RdbmsConnection connection = new RdbmsConnection(displayName, DbType.POSTGRESQL, "localhost", 5432, "db",
                "user", "enc", 1, null, Instant.now(), Instant.now());
        setField(connection, "id", id);
        return connection;
    }

    private static void setField(Object target, String fieldName, Object value) {
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
