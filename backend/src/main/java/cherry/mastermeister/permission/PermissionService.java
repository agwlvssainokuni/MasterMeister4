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
import cherry.mastermeister.audit.entity.AuditEventType;
import cherry.mastermeister.audit.entity.ResultStatus;
import cherry.mastermeister.audit.event.AuditEvent;
import cherry.mastermeister.permission.entity.AccessPermission;
import cherry.mastermeister.permission.entity.PrimaryPermission;
import cherry.mastermeister.permission.entity.PrincipalType;
import cherry.mastermeister.permission.repository.AccessPermissionRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * COMP-10の一部（PermissionService、AccessControlServiceから分割・改称。
 * GroupServiceとの命名一貫性のため。unit-04/nfr-design/logical-components.md §2）。
 * business-logic-model.md §1。BR-ACCESS-01・BR-ACCESS-12。
 */
@Service
public class PermissionService {

    private final AccessPermissionRepository accessPermissionRepository;
    private final AuditEventPublisher auditEventPublisher;

    public PermissionService(AccessPermissionRepository accessPermissionRepository,
                              AuditEventPublisher auditEventPublisher) {
        this.accessPermissionRepository = accessPermissionRepository;
        this.auditEventPublisher = auditEventPublisher;
    }

    @Transactional(readOnly = true)
    public List<AccessPermission> listPermissions(Long connectionId, PrincipalType principalType, Long principalId) {
        return accessPermissionRepository.findAllByConnectionIdAndPrincipalTypeAndPrincipalId(connectionId,
                principalType, principalId);
    }

    /**
     * BR-ACCESS-01: 同一キーへの再設定はupsert。カラム階層（columnName設定時）の補助権限は
     * AccessPermission.updatePermission()内で常にfalseへ強制される（FR-2.5）。
     */
    @CacheEvict(cacheNames = "effectivePermission", allEntries = true)
    @Transactional
    public AccessPermission setPermission(Long connectionId, PrincipalType principalType, Long principalId,
                                           String schemaName, String tableName, String columnName,
                                           PrimaryPermission primaryPermission, boolean createPermission,
                                           boolean deletePermission, Long updatedBy) {
        Instant now = Instant.now();
        AccessPermission permission = findExisting(connectionId, principalType, principalId, schemaName, tableName,
                columnName)
                .map(existing -> {
                    existing.updatePermission(primaryPermission, createPermission, deletePermission, now, updatedBy);
                    return existing;
                })
                .orElseGet(() -> accessPermissionRepository.save(new AccessPermission(connectionId, principalType,
                        principalId, schemaName, tableName, columnName, primaryPermission, createPermission,
                        deletePermission, now, updatedBy)));
        auditEventPublisher.publish(new AuditEvent(now, updatedBy, connectionId, AuditEventType.PERMISSION_CHANGED,
                resourceDescription(schemaName, tableName, columnName), ResultStatus.SUCCESS,
                describeValue(primaryPermission, createPermission, deletePermission)));
        return permission;
    }

    /**
     * BR-ACCESS-01: 「未設定」に戻す操作。対象行が存在しない場合は何もしない（冪等）。
     */
    @CacheEvict(cacheNames = "effectivePermission", allEntries = true)
    @Transactional
    public void unsetPermission(Long connectionId, PrincipalType principalType, Long principalId, String schemaName,
                                 String tableName, String columnName, Long updatedBy) {
        findExisting(connectionId, principalType, principalId, schemaName, tableName, columnName)
                .ifPresent(existing -> {
                    accessPermissionRepository.delete(existing);
                    auditEventPublisher.publish(new AuditEvent(Instant.now(), updatedBy, connectionId,
                            AuditEventType.PERMISSION_CHANGED, resourceDescription(schemaName, tableName, columnName),
                            ResultStatus.SUCCESS, "unset"));
                });
    }

    private Optional<AccessPermission> findExisting(Long connectionId, PrincipalType principalType,
                                                                Long principalId, String schemaName,
                                                                String tableName, String columnName) {
        return accessPermissionRepository
                .findByConnectionIdAndPrincipalTypeAndPrincipalIdAndSchemaNameAndTableNameRawAndColumnNameRaw(
                        connectionId, principalType, principalId, schemaName, AccessPermission.toSentinel(tableName),
                        AccessPermission.toSentinel(columnName));
    }

    private static String resourceDescription(String schemaName, String tableName, String columnName) {
        StringBuilder sb = new StringBuilder(schemaName);
        if (tableName != null) {
            sb.append('.').append(tableName);
            if (columnName != null) {
                sb.append('.').append(columnName);
            }
        }
        return sb.toString();
    }

    private static String describeValue(PrimaryPermission primaryPermission, boolean createPermission,
                                         boolean deletePermission) {
        return primaryPermission + (createPermission ? ",CREATE" : "") + (deletePermission ? ",DELETE" : "");
    }
}
