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
import cherry.mastermeister.common.exception.NonReadOnlyQueryException;
import cherry.mastermeister.common.exception.SavedQueryNotAccessibleException;
import cherry.mastermeister.query.entity.SavedQuery;
import cherry.mastermeister.query.entity.Visibility;
import cherry.mastermeister.query.model.VisibilityFilter;
import cherry.mastermeister.query.repository.SavedQueryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * COMP-15。business-logic-model.md §7〜8。BR-QUERY-05〜09。
 */
class SavedQueryServiceTest {

    private static final Long CONNECTION_ID = 100L;
    private static final Long OWNER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;

    private SavedQueryRepository savedQueryRepository;
    private SavedQueryService service;

    @BeforeEach
    void setUp() {
        savedQueryRepository = mock(SavedQueryRepository.class);
        when(savedQueryRepository.save(any(SavedQuery.class))).thenAnswer(invocation -> invocation.getArgument(0));
        service = new SavedQueryService(savedQueryRepository, mock(AuditEventPublisher.class));
    }

    @Test
    void saveQuery_rejectsNonReadOnlySql() {
        assertThatThrownBy(() -> service.saveQuery(OWNER_ID, CONNECTION_ID, "危険", "DELETE FROM t",
                Visibility.PRIVATE))
                .isInstanceOf(NonReadOnlyQueryException.class);
    }

    @Test
    void saveQuery_fixesConnectionIdAtSaveTime() {
        SavedQuery saved = service.saveQuery(OWNER_ID, CONNECTION_ID, "クエリ", "SELECT 1", Visibility.PUBLIC);

        assertThat(saved.getConnectionId()).isEqualTo(CONNECTION_ID);
        assertThat(saved.getCreatedBy()).isEqualTo(OWNER_ID);
        assertThat(saved.isRetired()).isFalse();
    }

    @Test
    void updateQuery_allowsCreatorToChangeAllFields() {
        SavedQuery existing = savedQuery(1L, OWNER_ID, Visibility.PRIVATE, false);
        when(savedQueryRepository.findById(1L)).thenReturn(Optional.of(existing));

        SavedQuery updated = service.updateQuery(OWNER_ID, CONNECTION_ID, 1L, "改名", "SELECT 2", Visibility.PUBLIC);

        assertThat(updated.getName()).isEqualTo("改名");
        assertThat(updated.getSql()).isEqualTo("SELECT 2");
        assertThat(updated.getVisibility()).isEqualTo(Visibility.PUBLIC);
    }

    @Test
    void updateQuery_rejectsNonOwner() {
        SavedQuery existing = savedQuery(1L, OWNER_ID, Visibility.PRIVATE, false);
        when(savedQueryRepository.findById(1L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.updateQuery(OTHER_USER_ID, CONNECTION_ID, 1L, "改名", "SELECT 2",
                Visibility.PUBLIC))
                .isInstanceOf(SavedQueryNotAccessibleException.class);
    }

    @Test
    void updateQuery_rejectsNonReadOnlySql() {
        SavedQuery existing = savedQuery(1L, OWNER_ID, Visibility.PRIVATE, false);
        when(savedQueryRepository.findById(1L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.updateQuery(OWNER_ID, CONNECTION_ID, 1L, "改名", "DELETE FROM t",
                Visibility.PUBLIC))
                .isInstanceOf(NonReadOnlyQueryException.class);
    }

    @Test
    void retireQuery_allowsOwnerOnly() {
        SavedQuery existing = savedQuery(1L, OWNER_ID, Visibility.PUBLIC, false);
        when(savedQueryRepository.findById(1L)).thenReturn(Optional.of(existing));

        service.retireQuery(OWNER_ID, CONNECTION_ID, 1L);

        assertThat(existing.isRetired()).isTrue();
    }

    @Test
    void retireQuery_rejectsNonOwner() {
        SavedQuery existing = savedQuery(1L, OWNER_ID, Visibility.PUBLIC, false);
        when(savedQueryRepository.findById(1L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.retireQuery(OTHER_USER_ID, CONNECTION_ID, 1L))
                .isInstanceOf(SavedQueryNotAccessibleException.class);
    }

    @Test
    void getSavedQuery_allowsPublicQueryForAnyUser() {
        SavedQuery existing = savedQuery(1L, OWNER_ID, Visibility.PUBLIC, false);
        when(savedQueryRepository.findById(1L)).thenReturn(Optional.of(existing));

        assertThat(service.getSavedQuery(OTHER_USER_ID, CONNECTION_ID, 1L)).isSameAs(existing);
    }

    @Test
    void getSavedQuery_rejectsPrivateQueryForNonOwner() {
        SavedQuery existing = savedQuery(1L, OWNER_ID, Visibility.PRIVATE, false);
        when(savedQueryRepository.findById(1L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.getSavedQuery(OTHER_USER_ID, CONNECTION_ID, 1L))
                .isInstanceOf(SavedQueryNotAccessibleException.class);
    }

    @Test
    void getSavedQuery_rejectsRetiredQueryForNonOwner_butAllowsOwner() {
        SavedQuery existing = savedQuery(1L, OWNER_ID, Visibility.PUBLIC, true);
        when(savedQueryRepository.findById(1L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.getSavedQuery(OTHER_USER_ID, CONNECTION_ID, 1L))
                .isInstanceOf(SavedQueryNotAccessibleException.class);
        assertThat(service.getSavedQuery(OWNER_ID, CONNECTION_ID, 1L)).isSameAs(existing);
    }

    @Test
    void getSavedQuery_rejectsMismatchedConnectionId() {
        SavedQuery existing = savedQuery(1L, OWNER_ID, Visibility.PUBLIC, false);
        when(savedQueryRepository.findById(1L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.getSavedQuery(OWNER_ID, 999L, 1L))
                .isInstanceOf(SavedQueryNotAccessibleException.class);
    }

    @Test
    void listSavedQueries_excludesRetiredByDefault_includesOwnRetiredWhenRequested() {
        SavedQuery active = savedQuery(1L, OWNER_ID, Visibility.PUBLIC, false);
        SavedQuery ownRetired = savedQuery(2L, OWNER_ID, Visibility.PRIVATE, true);
        SavedQuery othersRetired = savedQuery(3L, OTHER_USER_ID, Visibility.PUBLIC, true);
        when(savedQueryRepository.findAllByConnectionId(CONNECTION_ID))
                .thenReturn(List.of(active, ownRetired, othersRetired));

        List<SavedQuery> withoutRetired = service.listSavedQueries(OWNER_ID, CONNECTION_ID, VisibilityFilter.ALL,
                false);
        assertThat(withoutRetired).containsExactly(active);

        List<SavedQuery> withOwnRetired = service.listSavedQueries(OWNER_ID, CONNECTION_ID, VisibilityFilter.ALL,
                true);
        assertThat(withOwnRetired).containsExactlyInAnyOrder(active, ownRetired);
    }

    @Test
    void listSavedQueries_excludesOthersPrivateQueries() {
        SavedQuery ownPrivate = savedQuery(1L, OWNER_ID, Visibility.PRIVATE, false);
        SavedQuery othersPrivate = savedQuery(2L, OTHER_USER_ID, Visibility.PRIVATE, false);
        SavedQuery othersPublic = savedQuery(3L, OTHER_USER_ID, Visibility.PUBLIC, false);
        when(savedQueryRepository.findAllByConnectionId(CONNECTION_ID))
                .thenReturn(List.of(ownPrivate, othersPrivate, othersPublic));

        List<SavedQuery> result = service.listSavedQueries(OWNER_ID, CONNECTION_ID, VisibilityFilter.ALL, false);

        assertThat(result).containsExactlyInAnyOrder(ownPrivate, othersPublic);
    }

    @Test
    void listSavedQueries_appliesVisibilityFilter() {
        SavedQuery publicQuery = savedQuery(1L, OWNER_ID, Visibility.PUBLIC, false);
        SavedQuery privateQuery = savedQuery(2L, OWNER_ID, Visibility.PRIVATE, false);
        when(savedQueryRepository.findAllByConnectionId(CONNECTION_ID))
                .thenReturn(List.of(publicQuery, privateQuery));

        assertThat(service.listSavedQueries(OWNER_ID, CONNECTION_ID, VisibilityFilter.PUBLIC, false))
                .containsExactly(publicQuery);
        assertThat(service.listSavedQueries(OWNER_ID, CONNECTION_ID, VisibilityFilter.PRIVATE, false))
                .containsExactly(privateQuery);
    }

    private static SavedQuery savedQuery(Long id, Long createdBy, Visibility visibility, boolean retired) {
        Instant now = Instant.now();
        SavedQuery savedQuery = new SavedQuery(CONNECTION_ID, "クエリ" + id, "SELECT 1", visibility, createdBy, now);
        setId(savedQuery, id);
        if (retired) {
            savedQuery.retire(now);
        }
        return savedQuery;
    }

    private static void setId(SavedQuery savedQuery, Long id) {
        try {
            var field = SavedQuery.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(savedQuery, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
