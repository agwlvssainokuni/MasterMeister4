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
import cherry.mastermeister.common.exception.PermissionYamlImportRejectedException;
import cherry.mastermeister.group.entity.Group;
import cherry.mastermeister.group.repository.GroupRepository;
import cherry.mastermeister.permission.entity.AccessPermission;
import cherry.mastermeister.permission.entity.PrimaryPermission;
import cherry.mastermeister.permission.entity.PrincipalType;
import cherry.mastermeister.permission.repository.AccessPermissionRepository;
import cherry.mastermeister.registration.entity.User;
import cherry.mastermeister.registration.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * COMP-12。business-logic-model.md §3。BR-ACCESS-09〜10。
 * 検証フェーズ（プリンシパル解決・重複エントリチェック）とDB反映フェーズを分離する
 * （nfr-design-patterns.md §1.1）。
 */
@Service
public class PermissionYamlService {

    private final AccessPermissionRepository accessPermissionRepository;
    private final UserRepository userRepository;
    private final GroupRepository groupRepository;
    private final AuditEventPublisher auditEventPublisher;
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    public PermissionYamlService(AccessPermissionRepository accessPermissionRepository, UserRepository userRepository,
                                  GroupRepository groupRepository, AuditEventPublisher auditEventPublisher) {
        this.accessPermissionRepository = accessPermissionRepository;
        this.userRepository = userRepository;
        this.groupRepository = groupRepository;
        this.auditEventPublisher = auditEventPublisher;
    }

    @Transactional(readOnly = true)
    public String exportToYaml(Long connectionId, Long exportedBy) {
        List<AccessPermission> permissions = accessPermissionRepository.findAllByConnectionId(connectionId);
        List<PermissionYamlEntry> entries = permissions.stream().map(this::toYamlEntry).toList();
        String yaml;
        try {
            yaml = yamlMapper.writeValueAsString(new PermissionYamlDocument(connectionId, entries));
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("YAMLの生成に失敗しました", e);
        }
        auditEventPublisher.publish(new AuditEvent(Instant.now(), exportedBy, connectionId,
                AuditEventType.PERMISSION_YAML_EXPORTED, connectionId.toString(), ResultStatus.SUCCESS, null));
        return yaml;
    }

    /**
     * BR-ACCESS-10: 検証をすべて通過した場合のみ、既存権限設定を全削除し再構築する（全置換）。
     */
    @CacheEvict(cacheNames = "effectivePermission", allEntries = true)
    @Transactional
    public void importFromYaml(Long connectionId, String yaml, Long importedBy) {
        PermissionYamlDocument document;
        try {
            document = yamlMapper.readValue(yaml, PermissionYamlDocument.class);
        } catch (Exception e) {
            reject(connectionId, importedBy, "YAMLの解析に失敗しました: " + e.getMessage());
            return;
        }
        List<PermissionYamlEntry> entries = document.permissions() == null ? List.of() : document.permissions();

        List<ResolvedEntry> resolved = new ArrayList<>();
        List<String> unresolved = new ArrayList<>();
        for (PermissionYamlEntry entry : entries) {
            resolvePrincipal(entry).ifPresentOrElse(resolved::add,
                    () -> unresolved.add(entry.principalType() + ":" + entry.principal()));
        }
        if (!unresolved.isEmpty()) {
            reject(connectionId, importedBy, "未解決のプリンシパル: " + String.join(", ", unresolved));
            return;
        }

        Set<String> seenKeys = new HashSet<>();
        for (ResolvedEntry entry : resolved) {
            String key = entry.principalType() + ":" + entry.principalId() + ":" + entry.schemaName() + ":"
                    + AccessPermission.toSentinel(entry.tableName()) + ":" + AccessPermission.toSentinel(entry.columnName());
            if (!seenKeys.add(key)) {
                reject(connectionId, importedBy, "重複エントリ: " + key);
                return;
            }
        }

        accessPermissionRepository.deleteAll(accessPermissionRepository.findAllByConnectionId(connectionId));
        Instant now = Instant.now();
        for (ResolvedEntry entry : resolved) {
            accessPermissionRepository.save(new AccessPermission(connectionId, entry.principalType(),
                    entry.principalId(), entry.schemaName(), entry.tableName(), entry.columnName(),
                    PrimaryPermission.valueOf(entry.primaryPermission()), entry.createPermission(),
                    entry.deletePermission(), now, importedBy));
        }
        auditEventPublisher.publish(new AuditEvent(now, importedBy, connectionId,
                AuditEventType.PERMISSION_YAML_IMPORTED, connectionId.toString(), ResultStatus.SUCCESS, null));
    }

    private void reject(Long connectionId, Long importedBy, String reason) {
        auditEventPublisher.publish(new AuditEvent(Instant.now(), importedBy, connectionId,
                AuditEventType.PERMISSION_YAML_IMPORTED, connectionId.toString(), ResultStatus.FAILURE, reason));
        throw new PermissionYamlImportRejectedException(reason);
    }

    private Optional<ResolvedEntry> resolvePrincipal(PermissionYamlEntry entry) {
        PrincipalType principalType;
        try {
            principalType = PrincipalType.valueOf(entry.principalType());
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        Optional<Long> principalId = switch (principalType) {
            case USER -> userRepository.findByEmail(entry.principal()).map(User::getId);
            case GROUP -> groupRepository.findByName(entry.principal()).map(Group::getId);
        };
        return principalId.map(id -> new ResolvedEntry(principalType, id, entry.schemaName(), entry.tableName(),
                entry.columnName(), entry.primaryPermission(), entry.createPermission(), entry.deletePermission()));
    }

    private PermissionYamlEntry toYamlEntry(AccessPermission permission) {
        String principal = switch (permission.getPrincipalType()) {
            case USER -> userRepository.findById(permission.getPrincipalId()).map(User::getEmail).orElse("?");
            case GROUP -> groupRepository.findById(permission.getPrincipalId()).map(Group::getName).orElse("?");
        };
        return new PermissionYamlEntry(permission.getPrincipalType().name(), principal, permission.getSchemaName(),
                permission.getTableName(), permission.getColumnName(), permission.getPrimaryPermission().name(),
                permission.isCreatePermission(), permission.isDeletePermission());
    }

    private record ResolvedEntry(PrincipalType principalType, Long principalId, String schemaName, String tableName,
                                  String columnName, String primaryPermission, boolean createPermission,
                                  boolean deletePermission) {
    }
}
