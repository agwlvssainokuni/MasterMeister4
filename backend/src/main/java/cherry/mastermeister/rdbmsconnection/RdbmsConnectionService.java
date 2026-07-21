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
import cherry.mastermeister.audit.entity.ResultStatus;
import cherry.mastermeister.audit.event.AuditEvent;
import cherry.mastermeister.common.exception.RdbmsConnectionNotFoundException;
import cherry.mastermeister.rdbmsconnection.dialect.RdbmsDialectStrategy;
import cherry.mastermeister.rdbmsconnection.dialect.RdbmsDialectStrategyResolver;
import cherry.mastermeister.rdbmsconnection.entity.DbType;
import cherry.mastermeister.rdbmsconnection.entity.RdbmsConnection;
import cherry.mastermeister.rdbmsconnection.repository.RdbmsConnectionRepository;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * COMP-07。business-logic-model.md §1・§2・§4。BR-RDBMS-01〜05・09〜12。
 */
@Service
public class RdbmsConnectionService {

    private static final Logger log = LoggerFactory.getLogger(RdbmsConnectionService.class);

    private static final int HIKARI_MAX_POOL_SIZE = 5;
    private static final int HIKARI_MIN_IDLE = 0;
    private static final long HIKARI_CONNECTION_TIMEOUT_MS = 5000L;
    private static final int TEST_CONNECTION_LOGIN_TIMEOUT_SECONDS = 5;

    private final RdbmsConnectionRepository rdbmsConnectionRepository;
    private final ConnectionCredentialCipher connectionCredentialCipher;
    private final RdbmsDialectStrategyResolver dialectStrategyResolver;
    private final AuditEventPublisher auditEventPublisher;
    private final Map<Long, HikariDataSource> dataSourceCache = new ConcurrentHashMap<>();

    public RdbmsConnectionService(RdbmsConnectionRepository rdbmsConnectionRepository,
                                   ConnectionCredentialCipher connectionCredentialCipher,
                                   RdbmsDialectStrategyResolver dialectStrategyResolver,
                                   AuditEventPublisher auditEventPublisher) {
        this.rdbmsConnectionRepository = rdbmsConnectionRepository;
        this.connectionCredentialCipher = connectionCredentialCipher;
        this.dialectStrategyResolver = dialectStrategyResolver;
        this.auditEventPublisher = auditEventPublisher;
    }

    @Transactional(readOnly = true)
    public List<RdbmsConnection> listConnections() {
        return rdbmsConnectionRepository.findAll();
    }

    @Transactional(readOnly = true)
    public RdbmsConnection getConnection(Long connectionId) {
        return findOrThrow(connectionId);
    }

    /**
     * BR-RDBMS-01（形式チェックのみ、実際の接続確認は行わない）、BR-RDBMS-02（重複登録の許容）。
     */
    @Transactional
    public RdbmsConnection registerConnection(String displayName, DbType dbType, String host, int port,
                                               String databaseName, String schemaName, String username,
                                               String rawPassword, String additionalParams, Long registeredBy) {
        Instant now = Instant.now();
        ConnectionCredentialCipher.EncryptedCredential encrypted = connectionCredentialCipher.encrypt(rawPassword);
        RdbmsConnection connection = new RdbmsConnection(displayName, dbType, host, port, databaseName, schemaName,
                username, encrypted.encryptedValue(), encrypted.keyId(), additionalParams, now, now);
        RdbmsConnection saved = rdbmsConnectionRepository.save(connection);
        auditEventPublisher.publish(new AuditEvent(now, registeredBy, saved.getId(),
                AuditEventType.CONNECTION_REGISTERED, saved.getDisplayName(), ResultStatus.SUCCESS, null));
        return saved;
    }

    /**
     * BR-RDBMS-03。rawPasswordがnull/空欄の場合は既存の暗号化パスワード・keyIdを保持する（BR-RDBMS-12）。
     */
    @Transactional
    public RdbmsConnection updateConnection(Long connectionId, String displayName, DbType dbType, String host,
                                             int port, String databaseName, String schemaName, String username,
                                             String rawPassword, String additionalParams, Long updatedBy) {
        RdbmsConnection connection = findOrThrow(connectionId);
        String encryptedPassword = connection.getEncryptedPassword();
        int encryptionKeyId = connection.getEncryptionKeyId();
        if (rawPassword != null && !rawPassword.isBlank()) {
            ConnectionCredentialCipher.EncryptedCredential encrypted = connectionCredentialCipher.encrypt(
                    rawPassword);
            encryptedPassword = encrypted.encryptedValue();
            encryptionKeyId = encrypted.keyId();
        }
        connection.update(displayName, dbType, host, port, databaseName, schemaName, username, encryptedPassword,
                encryptionKeyId, additionalParams, Instant.now());
        evictDataSource(connectionId);
        auditEventPublisher.publish(new AuditEvent(Instant.now(), updatedBy, connectionId,
                AuditEventType.CONNECTION_UPDATED, connection.getDisplayName(), ResultStatus.SUCCESS, null));
        return connection;
    }

    /**
     * BR-RDBMS-09。参照有無に関わらず無条件でカスケード削除する
     * （schema_snapshot以下はDBのON DELETE CASCADEで削除される）。
     */
    @Transactional
    public void deleteConnection(Long connectionId, Long deletedBy) {
        RdbmsConnection connection = findOrThrow(connectionId);
        String displayName = connection.getDisplayName();
        rdbmsConnectionRepository.delete(connection);
        evictDataSource(connectionId);
        auditEventPublisher.publish(new AuditEvent(Instant.now(), deletedBy, connectionId,
                AuditEventType.CONNECTION_DELETED, displayName, ResultStatus.SUCCESS, null));
    }

    /**
     * COMP-07既存メソッド。接続ごとにHikariCPのDataSourceを生成・キャッシュする
     * （nfr-design/logical-components.md §1、Q4=A）。
     */
    public DataSource getDataSource(Long connectionId) {
        return dataSourceCache.computeIfAbsent(connectionId, id -> buildDataSource(findOrThrow(id)));
    }

    private HikariDataSource buildDataSource(RdbmsConnection connection) {
        RdbmsDialectStrategy dialect = dialectStrategyResolver.resolve(connection.getDbType());
        String jdbcUrl = dialect.buildJdbcUrl(connection.getHost(), connection.getPort(),
                connection.getDatabaseName(), connection.getSchemaName(), connection.getAdditionalParams());
        String password = connectionCredentialCipher.decrypt(connection.getEncryptedPassword(),
                connection.getEncryptionKeyId());

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(connection.getUsername());
        config.setPassword(password);
        config.setMaximumPoolSize(HIKARI_MAX_POOL_SIZE);
        config.setMinimumIdle(HIKARI_MIN_IDLE);
        config.setConnectionTimeout(HIKARI_CONNECTION_TIMEOUT_MS);
        return new HikariDataSource(config);
    }

    private void evictDataSource(Long connectionId) {
        HikariDataSource removed = dataSourceCache.remove(connectionId);
        if (removed != null) {
            removed.close();
        }
    }

    /**
     * BR-RDBMS-04, BR-RDBMS-05。保存済み接続に対する接続テスト。
     */
    @Transactional(readOnly = true)
    public ConnectionTestOutcome testConnection(Long connectionId) {
        RdbmsConnection connection = findOrThrow(connectionId);
        String password = connectionCredentialCipher.decrypt(connection.getEncryptedPassword(),
                connection.getEncryptionKeyId());
        return testConnectionInternal(connection.getDbType(), connection.getHost(), connection.getPort(),
                connection.getDatabaseName(), connection.getSchemaName(), connection.getUsername(), password,
                connection.getAdditionalParams());
    }

    /**
     * BR-RDBMS-11。フォーム入力中の未保存の値に対する接続テスト。永続化は行わない。
     */
    public ConnectionTestOutcome testConnectionUnsaved(DbType dbType, String host, int port, String databaseName,
                                                        String schemaName, String username, String rawPassword,
                                                        String additionalParams) {
        return testConnectionInternal(dbType, host, port, databaseName, schemaName, username, rawPassword,
                additionalParams);
    }

    private ConnectionTestOutcome testConnectionInternal(DbType dbType, String host, int port, String databaseName,
                                                          String schemaName, String username, String password,
                                                          String additionalParams) {
        RdbmsDialectStrategy dialect = dialectStrategyResolver.resolve(dbType);
        String jdbcUrl = dialect.buildJdbcUrl(host, port, databaseName, schemaName, additionalParams);
        DriverManager.setLoginTimeout(TEST_CONNECTION_LOGIN_TIMEOUT_SECONDS);
        try (Connection connection = DriverManager.getConnection(jdbcUrl, username, password)) {
            return ConnectionTestOutcome.ofSuccess();
        } catch (SQLTimeoutException e) {
            log.info("Connection test timed out for {}", jdbcUrl, e);
            return ConnectionTestOutcome.ofFailure(ConnectionErrorCategory.TIMEOUT);
        } catch (SQLException e) {
            ConnectionErrorCategory category = classify(e);
            log.info("Connection test failed for {} (category={})", jdbcUrl, category, e);
            return ConnectionTestOutcome.ofFailure(category);
        }
    }

    /**
     * SQLStateに基づくベストエフォートの分類（BR-RDBMS-04）。JDBCドライバ間でSQLStateの
     * 粒度が完全には統一されていないため、厳密な網羅性は保証しない。
     */
    static ConnectionErrorCategory classify(SQLException e) {
        String sqlState = e.getSQLState();
        if (sqlState != null) {
            if (sqlState.startsWith("08")) {
                return ConnectionErrorCategory.CONNECTION_UNREACHABLE;
            }
            if (sqlState.startsWith("28")) {
                return ConnectionErrorCategory.AUTH_ERROR;
            }
        }
        String message = e.getMessage() == null ? "" : e.getMessage().toLowerCase();
        if (message.contains("access denied") || message.contains("password") || message.contains("authentication")) {
            return ConnectionErrorCategory.AUTH_ERROR;
        }
        if (message.contains("connect") || message.contains("unreachable") || message.contains("unknown host")) {
            return ConnectionErrorCategory.CONNECTION_UNREACHABLE;
        }
        return ConnectionErrorCategory.OTHER;
    }

    private RdbmsConnection findOrThrow(Long connectionId) {
        return rdbmsConnectionRepository.findById(connectionId)
                .orElseThrow(RdbmsConnectionNotFoundException::new);
    }
}
