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

package cherry.mastermeister.rdbmsconnection;

import cherry.mastermeister.audit.AuditEventPublisher;
import cherry.mastermeister.audit.entity.AuditEventType;
import cherry.mastermeister.audit.event.AuditEvent;
import cherry.mastermeister.common.exception.RdbmsConnectionNotFoundException;
import cherry.mastermeister.rdbmsconnection.dialect.RdbmsDialectStrategy;
import cherry.mastermeister.rdbmsconnection.dialect.RdbmsDialectStrategyResolver;
import cherry.mastermeister.rdbmsconnection.entity.DbType;
import cherry.mastermeister.rdbmsconnection.entity.RdbmsConnection;
import cherry.mastermeister.rdbmsconnection.repository.RdbmsConnectionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * COMP-07。business-logic-model.md §1・§2・§4。BR-RDBMS-01〜05・09〜12。
 */
@ExtendWith(MockitoExtension.class)
class RdbmsConnectionServiceTest {

    @Mock
    private RdbmsConnectionRepository rdbmsConnectionRepository;
    @Mock
    private ConnectionCredentialCipher connectionCredentialCipher;
    @Mock
    private RdbmsDialectStrategyResolver dialectStrategyResolver;
    @Mock
    private AuditEventPublisher auditEventPublisher;

    private RdbmsConnectionService service;

    @BeforeEach
    void setUp() {
        service = new RdbmsConnectionService(rdbmsConnectionRepository, connectionCredentialCipher,
                dialectStrategyResolver, auditEventPublisher);
    }

    private RdbmsConnection existingConnection() {
        Instant now = Instant.now();
        return new RdbmsConnection("接続1", DbType.MYSQL, "localhost", 3306, "mastermeister", "root",
                "encrypted-v1", 1, null, now, now);
    }

    @Test
    void registerConnection_encryptsPassword_savesAndPublishesEvent() {
        when(connectionCredentialCipher.encrypt("s3cr3t"))
                .thenReturn(new ConnectionCredentialCipher.EncryptedCredential("encrypted", 3));
        when(rdbmsConnectionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RdbmsConnection saved = service.registerConnection("表示名", DbType.POSTGRESQL, "host", 5432, "db",
                "user", "s3cr3t", "sslmode=require", 100L);

        assertThat(saved.getEncryptedPassword()).isEqualTo("encrypted");
        assertThat(saved.getEncryptionKeyId()).isEqualTo(3);
        verify(auditEventPublisher).publish(argThatEventType(AuditEventType.CONNECTION_REGISTERED));
    }

    @Test
    void registerConnection_allowsDuplicateHostPortDatabase_perBrRdbms02() {
        // BR-RDBMS-02: サービス層で重複チェックを行わないことの確認(repositoryへの存在チェック呼び出しがないこと)
        when(connectionCredentialCipher.encrypt(any()))
                .thenReturn(new ConnectionCredentialCipher.EncryptedCredential("encrypted", 1));
        when(rdbmsConnectionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.registerConnection("同じ名前", DbType.MYSQL, "host", 3306, "db", "user", "pw", null, 1L);

        verify(rdbmsConnectionRepository, never()).findAll();
    }

    @Test
    void updateConnection_withBlankPassword_keepsExistingEncryptedPasswordAndKeyId() {
        RdbmsConnection connection = existingConnection();
        when(rdbmsConnectionRepository.findById(1L)).thenReturn(Optional.of(connection));

        service.updateConnection(1L, "新表示名", DbType.MYSQL, "newhost", 3306, "db", "user", "", null, 200L);

        assertThat(connection.getEncryptedPassword()).isEqualTo("encrypted-v1");
        assertThat(connection.getEncryptionKeyId()).isEqualTo(1);
        assertThat(connection.getDisplayName()).isEqualTo("新表示名");
        verify(connectionCredentialCipher, never()).encrypt(any());
        verify(auditEventPublisher).publish(argThatEventType(AuditEventType.CONNECTION_UPDATED));
    }

    @Test
    void updateConnection_withNewPassword_reEncryptsAndUpdatesKeyId() {
        RdbmsConnection connection = existingConnection();
        when(rdbmsConnectionRepository.findById(1L)).thenReturn(Optional.of(connection));
        when(connectionCredentialCipher.encrypt("newpass"))
                .thenReturn(new ConnectionCredentialCipher.EncryptedCredential("encrypted-v2", 2));

        service.updateConnection(1L, "接続1", DbType.MYSQL, "localhost", 3306, "mastermeister", "root",
                "newpass", null, 200L);

        assertThat(connection.getEncryptedPassword()).isEqualTo("encrypted-v2");
        assertThat(connection.getEncryptionKeyId()).isEqualTo(2);
    }

    @Test
    void updateConnection_evictsCachedDataSource() {
        RdbmsConnection connection = existingConnection();
        when(rdbmsConnectionRepository.findById(1L)).thenReturn(Optional.of(connection));
        RdbmsDialectStrategy dialect = mock(RdbmsDialectStrategy.class);
        when(dialectStrategyResolver.resolve(DbType.MYSQL)).thenReturn(dialect);
        when(dialect.buildJdbcUrl(any(), anyInt(), any(), any())).thenReturn("jdbc:mysql://localhost:3306/x");
        when(connectionCredentialCipher.decrypt(any(), anyInt())).thenReturn("pw");

        service.getDataSource(1L);
        service.updateConnection(1L, "接続1", DbType.MYSQL, "localhost", 3306, "mastermeister", "root", "",
                null, 200L);
        service.getDataSource(1L);

        // キャッシュがエビクトされ、更新後に再度DataSourceを構築するため、resolve()が2回呼ばれる
        verify(dialectStrategyResolver, times(2)).resolve(DbType.MYSQL);
    }

    @Test
    void deleteConnection_deletesAndPublishesEvent() {
        RdbmsConnection connection = existingConnection();
        when(rdbmsConnectionRepository.findById(1L)).thenReturn(Optional.of(connection));

        service.deleteConnection(1L, 300L);

        verify(rdbmsConnectionRepository).delete(connection);
        verify(auditEventPublisher).publish(argThatEventType(AuditEventType.CONNECTION_DELETED));
    }

    @Test
    void updateConnection_throwsNotFound_whenConnectionDoesNotExist() {
        when(rdbmsConnectionRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateConnection(999L, "x", DbType.MYSQL, "h", 1, "d", "u", "p", null,
                1L)).isInstanceOf(RdbmsConnectionNotFoundException.class);
    }

    @Test
    void getDataSource_cachesDataSourcePerConnectionId() {
        RdbmsConnection connection = existingConnection();
        when(rdbmsConnectionRepository.findById(1L)).thenReturn(Optional.of(connection));
        RdbmsDialectStrategy dialect = mock(RdbmsDialectStrategy.class);
        when(dialectStrategyResolver.resolve(DbType.MYSQL)).thenReturn(dialect);
        when(dialect.buildJdbcUrl(any(), anyInt(), any(), any())).thenReturn("jdbc:mysql://localhost:3306/x");
        when(connectionCredentialCipher.decrypt(any(), anyInt())).thenReturn("pw");

        DataSource first = service.getDataSource(1L);
        DataSource second = service.getDataSource(1L);

        assertThat(first).isSameAs(second);
        verify(dialectStrategyResolver, times(1)).resolve(DbType.MYSQL);
    }

    // --- BR-RDBMS-04のエラー分類 ---

    @Test
    void classify_returnsConnectionUnreachable_forSqlState08() {
        SQLException e = new SQLException("connection failed", "08001");
        assertThat(RdbmsConnectionService.classify(e)).isEqualTo(ConnectionErrorCategory.CONNECTION_UNREACHABLE);
    }

    @Test
    void classify_returnsAuthError_forSqlState28() {
        SQLException e = new SQLException("Access denied for user", "28000");
        assertThat(RdbmsConnectionService.classify(e)).isEqualTo(ConnectionErrorCategory.AUTH_ERROR);
    }

    @Test
    void classify_returnsAuthError_whenMessageMentionsAccessDenied_withoutSqlState() {
        SQLException e = new SQLException("Access denied for user 'root'@'host'");
        assertThat(RdbmsConnectionService.classify(e)).isEqualTo(ConnectionErrorCategory.AUTH_ERROR);
    }

    @Test
    void classify_returnsOther_whenUnclassifiable() {
        SQLException e = new SQLException("something went wrong");
        assertThat(RdbmsConnectionService.classify(e)).isEqualTo(ConnectionErrorCategory.OTHER);
    }

    private AuditEvent argThatEventType(AuditEventType eventType) {
        return org.mockito.ArgumentMatchers.argThat(event -> event != null && event.eventType() == eventType);
    }
}
