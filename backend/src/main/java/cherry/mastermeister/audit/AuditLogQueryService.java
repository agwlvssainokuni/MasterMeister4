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
import cherry.mastermeister.audit.entity.AuditLogEntry;
import cherry.mastermeister.audit.repository.AuditLogEntryRepository;
import cherry.mastermeister.rdbmsconnection.entity.RdbmsConnection;
import cherry.mastermeister.rdbmsconnection.repository.RdbmsConnectionRepository;
import cherry.mastermeister.registration.entity.User;
import cherry.mastermeister.registration.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static cherry.mastermeister.audit.AuditLogSpecifications.connectionIdEquals;
import static cherry.mastermeister.audit.AuditLogSpecifications.eventTypeEquals;
import static cherry.mastermeister.audit.AuditLogSpecifications.occurredAtFrom;
import static cherry.mastermeister.audit.AuditLogSpecifications.occurredAtTo;
import static cherry.mastermeister.audit.AuditLogSpecifications.resultStatusEquals;
import static cherry.mastermeister.audit.AuditLogSpecifications.userIdEquals;

/**
 * COMP-18。business-logic-model.md §2〜6。絞込・ページング・名前解決の3責務を担う
 * （nfr-design-plan.md Q2=A）。記録専用の既存AuditLogServiceとは別クラスであり、
 * 記録処理には一切変更を加えない。本ユニットは管理者専用エンドポイントであり
 * ロール判定・実行者スコープのフィルタ変換は行わない（BR-AUDITVIEW-03）。
 */
@Service
public class AuditLogQueryService {

    private static final String UNKNOWN_USER_PLACEHOLDER = "(不明なユーザ)";
    private static final String DELETED_CONNECTION_PLACEHOLDER = "(削除済み接続)";

    private final AuditLogEntryRepository auditLogEntryRepository;
    private final UserRepository userRepository;
    private final RdbmsConnectionRepository rdbmsConnectionRepository;

    public AuditLogQueryService(AuditLogEntryRepository auditLogEntryRepository, UserRepository userRepository,
                                 RdbmsConnectionRepository rdbmsConnectionRepository) {
        this.auditLogEntryRepository = auditLogEntryRepository;
        this.userRepository = userRepository;
        this.rdbmsConnectionRepository = rdbmsConnectionRepository;
    }

    /**
     * business-logic-model.md §2・§6。BR-AUDITVIEW-06（絞込条件はすべてAND結合）。
     */
    public Page<AuditLogEntryResponse> listAuditLog(AuditLogSearchCriteria criteria, Pageable pageable) {
        Specification<AuditLogEntry> spec = (root, query, cb) -> cb.conjunction();
        if (criteria.occurredAtFrom() != null) {
            spec = spec.and(occurredAtFrom(criteria.occurredAtFrom()));
        }
        if (criteria.occurredAtTo() != null) {
            spec = spec.and(occurredAtTo(criteria.occurredAtTo()));
        }
        if (criteria.eventType() != null) {
            spec = spec.and(eventTypeEquals(criteria.eventType()));
        }
        if (criteria.userId() != null) {
            spec = spec.and(userIdEquals(criteria.userId()));
        }
        if (criteria.connectionId() != null) {
            spec = spec.and(connectionIdEquals(criteria.connectionId()));
        }
        if (criteria.resultStatus() != null) {
            spec = spec.and(resultStatusEquals(criteria.resultStatus()));
        }

        Page<AuditLogEntry> page = auditLogEntryRepository.findAll(spec, pageable);

        Set<Long> userIds = page.getContent().stream()
                .map(AuditLogEntry::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, User> users = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        Set<Long> connectionIds = page.getContent().stream()
                .map(AuditLogEntry::getConnectionId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, RdbmsConnection> connections = rdbmsConnectionRepository.findAllById(connectionIds).stream()
                .collect(Collectors.toMap(RdbmsConnection::getId, c -> c));

        return page.map(entry -> toResponse(entry, users, connections));
    }

    private AuditLogEntryResponse toResponse(AuditLogEntry entry, Map<Long, User> users,
                                              Map<Long, RdbmsConnection> connections) {
        Long userId = entry.getUserId();
        String userDisplayName = null;
        if (userId != null) {
            User user = users.get(userId);
            userDisplayName = user != null ? user.getFullName() : UNKNOWN_USER_PLACEHOLDER;
        }

        Long connectionId = entry.getConnectionId();
        String connectionDisplayName = null;
        if (connectionId != null) {
            RdbmsConnection connection = connections.get(connectionId);
            connectionDisplayName = connection != null ? connection.getDisplayName() : DELETED_CONNECTION_PLACEHOLDER;
        }

        return new AuditLogEntryResponse(entry.getId(), entry.getOccurredAt(), userId, userDisplayName,
                connectionId, connectionDisplayName, entry.getEventType(), entry.getTargetResource(),
                entry.getResultStatus(), entry.getDetail());
    }
}
