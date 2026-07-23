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

package cherry.mastermeister.permission.repository;

import cherry.mastermeister.permission.entity.AccessPermission;
import cherry.mastermeister.permission.entity.PrimaryPermission;
import cherry.mastermeister.permission.entity.PrincipalType;
import cherry.mastermeister.rdbmsconnection.entity.DbType;
import cherry.mastermeister.rdbmsconnection.entity.RdbmsConnection;
import cherry.mastermeister.rdbmsconnection.repository.RdbmsConnectionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class AccessPermissionRepositoryTest {

    @Autowired
    private AccessPermissionRepository accessPermissionRepository;

    @Autowired
    private RdbmsConnectionRepository rdbmsConnectionRepository;

    private Long persistConnection() {
        Instant now = Instant.now();
        RdbmsConnection connection = new RdbmsConnection("動作確認用", DbType.MYSQL, "localhost", 3306,
                "mastermeister", "root", "encrypted", 1, null, now, now);
        return rdbmsConnectionRepository.saveAndFlush(connection).getId();
    }

    @Test
    void save_persistsSchemaLevelPermissionWithSentinelValues() {
        Long connectionId = persistConnection();
        AccessPermission permission = new AccessPermission(connectionId, PrincipalType.USER, 1L, "public", null,
                null, PrimaryPermission.READ, true, false, Instant.now(), 99L);

        accessPermissionRepository.saveAndFlush(permission);

        AccessPermission reloaded = accessPermissionRepository.findById(permission.getId()).orElseThrow();
        assertThat(reloaded.getTableName()).isNull();
        assertThat(reloaded.getColumnName()).isNull();
        assertThat(reloaded.getSchemaName()).isEqualTo("public");
    }

    @Test
    void save_rejectsDuplicateKey() {
        Long connectionId = persistConnection();
        accessPermissionRepository.saveAndFlush(new AccessPermission(connectionId, PrincipalType.USER, 1L, "public",
                "products", null, PrimaryPermission.READ, false, false, Instant.now(), 99L));

        assertThatThrownBy(() -> accessPermissionRepository.saveAndFlush(new AccessPermission(connectionId,
                PrincipalType.USER, 1L, "public", "products", null, PrimaryPermission.UPDATE, false, false,
                Instant.now(), 99L)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void findAllByConnectionIdAndPrincipalTypeAndPrincipalId_returnsOnlyMatchingPrincipal() {
        Long connectionId = persistConnection();
        accessPermissionRepository.saveAndFlush(new AccessPermission(connectionId, PrincipalType.USER, 1L, "public",
                "products", null, PrimaryPermission.READ, false, false, Instant.now(), 99L));
        accessPermissionRepository.saveAndFlush(new AccessPermission(connectionId, PrincipalType.GROUP, 2L, "public",
                "orders", null, PrimaryPermission.UPDATE, false, false, Instant.now(), 99L));

        var result = accessPermissionRepository.findAllByConnectionIdAndPrincipalTypeAndPrincipalId(connectionId,
                PrincipalType.USER, 1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTableName()).isEqualTo("products");
    }

    @Test
    void deleteAllByPrincipalTypeAndPrincipalId_removesOnlyMatchingGroup() {
        Long connectionId = persistConnection();
        accessPermissionRepository.saveAndFlush(new AccessPermission(connectionId, PrincipalType.GROUP, 10L, "public",
                "products", null, PrimaryPermission.READ, false, false, Instant.now(), 99L));
        accessPermissionRepository.saveAndFlush(new AccessPermission(connectionId, PrincipalType.GROUP, 20L, "public",
                "orders", null, PrimaryPermission.READ, false, false, Instant.now(), 99L));

        accessPermissionRepository.deleteAllByPrincipalTypeAndPrincipalId(PrincipalType.GROUP, 10L);
        accessPermissionRepository.flush();

        assertThat(accessPermissionRepository.findAllByConnectionId(connectionId)).hasSize(1);
    }

    @Test
    void findByCompositeKey_locatesUpsertTargetUsingSentinelValues() {
        Long connectionId = persistConnection();
        accessPermissionRepository.saveAndFlush(new AccessPermission(connectionId, PrincipalType.USER, 1L, "public",
                null, null, PrimaryPermission.READ, true, false, Instant.now(), 99L));

        var found = accessPermissionRepository
                .findByConnectionIdAndPrincipalTypeAndPrincipalIdAndSchemaNameAndTableNameRawAndColumnNameRaw(
                        connectionId, PrincipalType.USER, 1L, "public", "", "");

        assertThat(found).isPresent();
    }
}
