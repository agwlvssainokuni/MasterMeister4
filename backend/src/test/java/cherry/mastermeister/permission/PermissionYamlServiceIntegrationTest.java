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
import cherry.mastermeister.group.entity.Group;
import cherry.mastermeister.group.repository.GroupRepository;
import cherry.mastermeister.permission.entity.AccessPermission;
import cherry.mastermeister.permission.entity.PrimaryPermission;
import cherry.mastermeister.permission.entity.PrincipalType;
import cherry.mastermeister.permission.repository.AccessPermissionRepository;
import cherry.mastermeister.rdbmsconnection.entity.DbType;
import cherry.mastermeister.rdbmsconnection.entity.RdbmsConnection;
import cherry.mastermeister.rdbmsconnection.repository.RdbmsConnectionRepository;
import cherry.mastermeister.registration.entity.Language;
import cherry.mastermeister.registration.entity.Role;
import cherry.mastermeister.registration.entity.User;
import cherry.mastermeister.registration.entity.UserStatus;
import cherry.mastermeister.registration.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * 実機（devenvのMySQL接続）でのcurl検証時に発見した不具合の再現テスト。
 * Hibernateのデフォルトフラッシュ順序では同一フラッシュ内でDELETEがINSERTより後に実行されるため、
 * importFromYaml()内で「既存行を全削除→即save()で同一キーの行を再作成」する際、削除前の行が
 * 物理的に残った状態で同一キーのINSERTが実行され複合UNIQUE制約違反になっていた。
 * Mockitoによるユニットテスト（PermissionYamlServiceTest/PropertyTest）はリポジトリを簡易な
 * インメモリ実装で代替していたため、この実際のHibernateフラッシュ順序の問題を検出できなかった
 * （実DBを使う本テストで初めて再現・検証できる）。
 */
@DataJpaTest
class PermissionYamlServiceIntegrationTest {

    @Autowired
    private AccessPermissionRepository accessPermissionRepository;
    @Autowired
    private RdbmsConnectionRepository rdbmsConnectionRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private GroupRepository groupRepository;

    @Test
    void importFromYaml_reimportingSameKeysDoesNotViolateUniqueConstraint() {
        Instant now = Instant.now();
        RdbmsConnection connection = rdbmsConnectionRepository.saveAndFlush(new RdbmsConnection("動作確認用",
                DbType.MYSQL, "localhost", 3306, "mastermeister", "root", "encrypted", 1, null, now, now));
        Long connectionId = connection.getId();

        User user = userRepository.saveAndFlush(new User("alice@example.com", "hash", "Alice", Language.ja,
                UserStatus.APPROVED, Role.USER, now, now, null));
        Group group = groupRepository.saveAndFlush(new Group("営業", now));

        accessPermissionRepository.saveAndFlush(new AccessPermission(connectionId, PrincipalType.USER,
                user.getId(), "public", "products", null, PrimaryPermission.READ, false, false, now, 1L));
        accessPermissionRepository.saveAndFlush(new AccessPermission(connectionId, PrincipalType.GROUP,
                group.getId(), "public", "products", null, PrimaryPermission.UPDATE, true, true, now, 1L));

        PermissionYamlService service = new PermissionYamlService(accessPermissionRepository, userRepository,
                groupRepository, mock(AuditEventPublisher.class));

        String yaml = service.exportToYaml(connectionId, 1L);
        // 同一キー（プリンシパル×スキーマ×テーブル）構成のまま再インポートする
        // （このシナリオが実機検証でユニーク制約違反を引き起こしていた）。
        service.importFromYaml(connectionId, yaml, 1L);

        assertThat(accessPermissionRepository.findAllByConnectionId(connectionId)).hasSize(2);
    }
}
