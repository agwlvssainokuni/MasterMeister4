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

package cherry.mastermeister.audit;

import cherry.mastermeister.audit.dto.AuditLogEntryResponse;
import cherry.mastermeister.audit.dto.AuditLogSearchCriteria;
import cherry.mastermeister.audit.entity.AuditEventType;
import cherry.mastermeister.audit.entity.AuditLogEntry;
import cherry.mastermeister.audit.entity.ResultStatus;
import cherry.mastermeister.audit.repository.AuditLogEntryRepository;
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
 * COMP-18。business-logic-model.md §2〜6。BR-AUDITVIEW-01〜11。
 */
class AuditLogQueryServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long CONNECTION_ID = 100L;

    private AuditLogEntryRepository auditLogEntryRepository;
    private UserRepository userRepository;
    private RdbmsConnectionRepository rdbmsConnectionRepository;
    private AuditLogQueryService service;

    @BeforeEach
    void setUp() {
        auditLogEntryRepository = mock(AuditLogEntryRepository.class);
        userRepository = mock(UserRepository.class);
        rdbmsConnectionRepository = mock(RdbmsConnectionRepository.class);
        service = new AuditLogQueryService(auditLogEntryRepository, userRepository, rdbmsConnectionRepository);
    }

    @Test
    void listAuditLog_resolvesUserAndConnectionDisplayName() {
        AuditLogEntry entry = entry(1L, USER_ID, CONNECTION_ID, AuditEventType.QUERY_EXECUTED);
        Pageable pageable = PageRequest.of(0, 20);
        mockFindAll(entry, pageable);
        when(userRepository.findAllById(any())).thenReturn(List.of(user(USER_ID, "山田太郎")));
        when(rdbmsConnectionRepository.findAllById(any())).thenReturn(List.of(rdbmsConnection(CONNECTION_ID, "接続A")));

        Page<AuditLogEntryResponse> result = service.listAuditLog(emptyCriteria(), pageable);

        AuditLogEntryResponse response = result.getContent().get(0);
        assertThat(response.userDisplayName()).isEqualTo("山田太郎");
        assertThat(response.connectionDisplayName()).isEqualTo("接続A");
    }

    @Test
    void listAuditLog_showsPlaceholderForUnknownUser() {
        AuditLogEntry entry = entry(2L, USER_ID, CONNECTION_ID, AuditEventType.LOGIN);
        Pageable pageable = PageRequest.of(0, 20);
        mockFindAll(entry, pageable);
        when(userRepository.findAllById(any())).thenReturn(List.of());
        when(rdbmsConnectionRepository.findAllById(any())).thenReturn(List.of());

        Page<AuditLogEntryResponse> result = service.listAuditLog(emptyCriteria(), pageable);

        assertThat(result.getContent().get(0).userDisplayName()).isEqualTo("(不明なユーザ)");
    }

    @Test
    void listAuditLog_showsPlaceholderForDeletedConnection() {
        AuditLogEntry entry = entry(3L, USER_ID, CONNECTION_ID, AuditEventType.QUERY_EXECUTED);
        Pageable pageable = PageRequest.of(0, 20);
        mockFindAll(entry, pageable);
        when(userRepository.findAllById(any())).thenReturn(List.of(user(USER_ID, "山田太郎")));
        when(rdbmsConnectionRepository.findAllById(any())).thenReturn(List.of());

        Page<AuditLogEntryResponse> result = service.listAuditLog(emptyCriteria(), pageable);

        assertThat(result.getContent().get(0).connectionDisplayName()).isEqualTo("(削除済み接続)");
    }

    @Test
    void listAuditLog_withoutUserOrConnection_returnsNullDisplayNames() {
        AuditLogEntry entry = new AuditLogEntry(Instant.now(), null, null, AuditEventType.LOGIN, null,
                ResultStatus.SUCCESS, null);
        setField(entry, "id", 4L);
        Pageable pageable = PageRequest.of(0, 20);
        mockFindAll(entry, pageable);
        when(userRepository.findAllById(any())).thenReturn(List.of());
        when(rdbmsConnectionRepository.findAllById(any())).thenReturn(List.of());

        Page<AuditLogEntryResponse> result = service.listAuditLog(emptyCriteria(), pageable);

        AuditLogEntryResponse response = result.getContent().get(0);
        assertThat(response.userDisplayName()).isNull();
        assertThat(response.connectionDisplayName()).isNull();
    }

    private void mockFindAll(AuditLogEntry entry, Pageable pageable) {
        when(auditLogEntryRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(entry), pageable, 1));
    }

    private static AuditLogSearchCriteria emptyCriteria() {
        return new AuditLogSearchCriteria(null, null, null, null, null, null);
    }

    private static AuditLogEntry entry(Long id, Long userId, Long connectionId, AuditEventType eventType) {
        AuditLogEntry entry = new AuditLogEntry(Instant.now(), userId, connectionId, eventType, null,
                ResultStatus.SUCCESS, null);
        setField(entry, "id", id);
        return entry;
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
