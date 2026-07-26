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

package cherry.mastermeister.query;

import cherry.mastermeister.audit.AuditEventPublisher;
import cherry.mastermeister.audit.entity.AuditEventType;
import cherry.mastermeister.audit.entity.ResultStatus;
import cherry.mastermeister.audit.event.AuditEvent;
import cherry.mastermeister.common.exception.NonReadOnlyQueryException;
import cherry.mastermeister.common.exception.SavedQueryNotAccessibleException;
import cherry.mastermeister.query.entity.SavedQuery;
import cherry.mastermeister.query.entity.Visibility;
import cherry.mastermeister.query.model.VisibilityFilter;
import cherry.mastermeister.query.repository.SavedQueryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * COMP-15。business-logic-model.md §7〜8。保存クエリの保存・編集・非表示化・アクセス可否判定を担う。
 */
@Service
public class SavedQueryService {

    private final SavedQueryRepository savedQueryRepository;
    private final AuditEventPublisher auditEventPublisher;

    public SavedQueryService(SavedQueryRepository savedQueryRepository, AuditEventPublisher auditEventPublisher) {
        this.savedQueryRepository = savedQueryRepository;
        this.auditEventPublisher = auditEventPublisher;
    }

    /**
     * BR-QUERY-05。保存前にBR-QUERY-01の読み取り専用検証を行う。接続は保存時点で固定する（Q11）。
     */
    @Transactional
    public SavedQuery saveQuery(Long userId, Long connectionId, String name, String sql, Visibility visibility) {
        if (!new QuerySqlAnalyzer(sql).isReadOnly()) {
            throw new NonReadOnlyQueryException();
        }
        Instant now = Instant.now();
        SavedQuery saved = savedQueryRepository.save(new SavedQuery(connectionId, name, sql, visibility, userId, now));
        auditEventPublisher.publish(new AuditEvent(now, userId, connectionId, AuditEventType.QUERY_SAVED, name,
                ResultStatus.SUCCESS, visibility.name()));
        return saved;
    }

    /**
     * BR-QUERY-07。作成者のみ実行可能。SQL変更時は読み取り専用検証を再度行う。
     * findOwned〜mutate〜returnを単一トランザクション内で行い、Hibernateのダーティチェックで
     * 変更を永続化する（GroupService.renameGroupと同じ方式。実機E2E検証で、@Transactionalなしでは
     * 変更が永続化されないバグを発見し修正）。
     */
    @Transactional
    public SavedQuery updateQuery(Long userId, Long connectionId, Long savedQueryId, String name, String sql,
                                   Visibility visibility) {
        SavedQuery savedQuery = getOwned(userId, connectionId, savedQueryId);
        if (!new QuerySqlAnalyzer(sql).isReadOnly()) {
            throw new NonReadOnlyQueryException();
        }
        Instant now = Instant.now();
        savedQuery.update(name, sql, visibility, now);
        auditEventPublisher.publish(new AuditEvent(now, userId, connectionId, AuditEventType.QUERY_UPDATED, name,
                ResultStatus.SUCCESS, null));
        return savedQuery;
    }

    /**
     * BR-QUERY-08。作成者のみ実行可能。un-retireは提供しない。
     */
    @Transactional
    public void retireQuery(Long userId, Long connectionId, Long savedQueryId) {
        SavedQuery savedQuery = getOwned(userId, connectionId, savedQueryId);
        Instant now = Instant.now();
        savedQuery.retire(now);
        auditEventPublisher.publish(new AuditEvent(now, userId, connectionId, AuditEventType.QUERY_RETIRED,
                savedQuery.getName(), ResultStatus.SUCCESS, null));
    }

    /**
     * BR-QUERY-09。閲覧・実行時のアクセス可否判定。存在有無と権限有無を区別しないフェイルクローズ。
     */
    @Transactional(readOnly = true)
    public SavedQuery getSavedQuery(Long userId, Long connectionId, Long savedQueryId) {
        SavedQuery savedQuery = findInConnection(connectionId, savedQueryId);
        if (!isAccessible(userId, savedQuery)) {
            throw new SavedQueryNotAccessibleException();
        }
        return savedQuery;
    }

    /**
     * BR-QUERY-05, BR-QUERY-08。visibilityFilter・includeOwnRetiredで絞り込んだ一覧を返す。
     */
    @Transactional(readOnly = true)
    public List<SavedQuery> listSavedQueries(Long userId, Long connectionId, VisibilityFilter visibilityFilter,
                                              boolean includeOwnRetired) {
        return savedQueryRepository.findAllByConnectionId(connectionId).stream()
                .filter(q -> isListable(userId, q, includeOwnRetired))
                .filter(q -> matchesVisibilityFilter(q, visibilityFilter))
                .toList();
    }

    private boolean isListable(Long userId, SavedQuery savedQuery, boolean includeOwnRetired) {
        boolean isOwner = savedQuery.getCreatedBy().equals(userId);
        if (savedQuery.isRetired()) {
            return isOwner && includeOwnRetired;
        }
        return savedQuery.getVisibility() == Visibility.PUBLIC || isOwner;
    }

    private boolean matchesVisibilityFilter(SavedQuery savedQuery, VisibilityFilter filter) {
        return switch (filter) {
            case ALL -> true;
            case PUBLIC -> savedQuery.getVisibility() == Visibility.PUBLIC;
            case PRIVATE -> savedQuery.getVisibility() == Visibility.PRIVATE;
        };
    }

    private boolean isAccessible(Long userId, SavedQuery savedQuery) {
        boolean isOwner = savedQuery.getCreatedBy().equals(userId);
        if (savedQuery.isRetired() && !isOwner) {
            return false;
        }
        return savedQuery.getVisibility() == Visibility.PUBLIC || isOwner;
    }

    private SavedQuery getOwned(Long userId, Long connectionId, Long savedQueryId) {
        SavedQuery savedQuery = findInConnection(connectionId, savedQueryId);
        if (!savedQuery.getCreatedBy().equals(userId)) {
            throw new SavedQueryNotAccessibleException();
        }
        return savedQuery;
    }

    private SavedQuery findInConnection(Long connectionId, Long savedQueryId) {
        return savedQueryRepository.findById(savedQueryId)
                .filter(q -> q.getConnectionId().equals(connectionId))
                .orElseThrow(SavedQueryNotAccessibleException::new);
    }
}
