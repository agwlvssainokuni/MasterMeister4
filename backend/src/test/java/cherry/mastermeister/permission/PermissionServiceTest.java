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

package cherry.mastermeister.permission;

import cherry.mastermeister.audit.AuditEventPublisher;
import cherry.mastermeister.permission.entity.AccessPermission;
import cherry.mastermeister.permission.entity.PrimaryPermission;
import cherry.mastermeister.permission.entity.PrincipalType;
import cherry.mastermeister.permission.repository.AccessPermissionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PermissionServiceTest {

    @Mock
    private AccessPermissionRepository accessPermissionRepository;
    @Mock
    private AuditEventPublisher auditEventPublisher;

    private PermissionService service;

    @BeforeEach
    void setUp() {
        service = new PermissionService(accessPermissionRepository, auditEventPublisher);
    }

    @Test
    void setPermission_createsNewEntryWhenNoneExists() {
        when(accessPermissionRepository
                .findByConnectionIdAndPrincipalTypeAndPrincipalIdAndSchemaNameAndTableNameRawAndColumnNameRaw(
                        1L, PrincipalType.USER, 42L, "public", "products", ""))
                .thenReturn(Optional.empty());
        when(accessPermissionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AccessPermission result = service.setPermission(1L, PrincipalType.USER, 42L, "public", "products", null,
                PrimaryPermission.READ, true, false, 99L);

        assertThat(result.getPrimaryPermission()).isEqualTo(PrimaryPermission.READ);
        assertThat(result.isCreatePermission()).isTrue();
        verify(auditEventPublisher, times(1)).publish(any());
    }

    @Test
    void setPermission_updatesExistingEntry() {
        AccessPermission existing = new AccessPermission(1L, PrincipalType.USER, 42L, "public", "products", null,
                PrimaryPermission.READ, false, false, Instant.now(), 1L);
        when(accessPermissionRepository
                .findByConnectionIdAndPrincipalTypeAndPrincipalIdAndSchemaNameAndTableNameRawAndColumnNameRaw(
                        1L, PrincipalType.USER, 42L, "public", "products", ""))
                .thenReturn(Optional.of(existing));

        AccessPermission result = service.setPermission(1L, PrincipalType.USER, 42L, "public", "products", null,
                PrimaryPermission.UPDATE, true, true, 99L);

        assertThat(result).isSameAs(existing);
        assertThat(result.getPrimaryPermission()).isEqualTo(PrimaryPermission.UPDATE);
        verify(accessPermissionRepository, never()).save(any());
    }

    @Test
    void setPermission_forcesAuxiliaryPermissionsFalseAtColumnLevel() {
        when(accessPermissionRepository
                .findByConnectionIdAndPrincipalTypeAndPrincipalIdAndSchemaNameAndTableNameRawAndColumnNameRaw(
                        1L, PrincipalType.USER, 42L, "public", "products", "price"))
                .thenReturn(Optional.empty());
        when(accessPermissionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AccessPermission result = service.setPermission(1L, PrincipalType.USER, 42L, "public", "products", "price",
                PrimaryPermission.UPDATE, true, true, 99L);

        assertThat(result.isCreatePermission()).isFalse();
        assertThat(result.isDeletePermission()).isFalse();
    }

    @Test
    void unsetPermission_isIdempotentWhenNoneExists() {
        when(accessPermissionRepository
                .findByConnectionIdAndPrincipalTypeAndPrincipalIdAndSchemaNameAndTableNameRawAndColumnNameRaw(
                        1L, PrincipalType.USER, 42L, "public", "products", ""))
                .thenReturn(Optional.empty());

        service.unsetPermission(1L, PrincipalType.USER, 42L, "public", "products", null, 99L);

        verify(accessPermissionRepository, never()).delete(any());
        verify(auditEventPublisher, never()).publish(any());
    }

    @Test
    void unsetPermission_deletesExistingEntry() {
        AccessPermission existing = new AccessPermission(1L, PrincipalType.USER, 42L, "public", "products", null,
                PrimaryPermission.READ, false, false, Instant.now(), 1L);
        when(accessPermissionRepository
                .findByConnectionIdAndPrincipalTypeAndPrincipalIdAndSchemaNameAndTableNameRawAndColumnNameRaw(
                        1L, PrincipalType.USER, 42L, "public", "products", ""))
                .thenReturn(Optional.of(existing));

        service.unsetPermission(1L, PrincipalType.USER, 42L, "public", "products", null, 99L);

        verify(accessPermissionRepository, times(1)).delete(existing);
        verify(auditEventPublisher, times(1)).publish(any());
    }
}
