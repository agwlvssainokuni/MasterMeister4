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

package cherry.mastermeister.rdbmsconnection.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * domain-entities.md §1。host/port/databaseNameの重複は許容する（BR-RDBMS-02）ため
 * 一意制約は設けない。
 */
@Entity
@Table(name = "rdbms_connection")
public class RdbmsConnection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(name = "db_type", nullable = false, length = 20)
    private DbType dbType;

    @Column(nullable = false)
    private String host;

    @Column(nullable = false)
    private int port;

    @Column(name = "database_name", nullable = false)
    private String databaseName;

    @Column(name = "schema_name")
    private String schemaName;

    @Column(nullable = false)
    private String username;

    @Column(name = "encrypted_password", nullable = false, length = 500)
    private String encryptedPassword;

    @Column(name = "encryption_key_id", nullable = false)
    private int encryptionKeyId;

    @Column(name = "additional_params", length = 1000)
    private String additionalParams;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected RdbmsConnection() {
        // JPA
    }

    public RdbmsConnection(String displayName, DbType dbType, String host, int port, String databaseName,
                            String schemaName, String username, String encryptedPassword, int encryptionKeyId,
                            String additionalParams, Instant createdAt, Instant updatedAt) {
        this.displayName = displayName;
        this.dbType = dbType;
        this.host = host;
        this.port = port;
        this.databaseName = databaseName;
        this.schemaName = schemaName;
        this.username = username;
        this.encryptedPassword = encryptedPassword;
        this.encryptionKeyId = encryptionKeyId;
        this.additionalParams = additionalParams;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public DbType getDbType() {
        return dbType;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getDatabaseName() {
        return databaseName;
    }

    public String getSchemaName() {
        return schemaName;
    }

    public String getUsername() {
        return username;
    }

    public String getEncryptedPassword() {
        return encryptedPassword;
    }

    public int getEncryptionKeyId() {
        return encryptionKeyId;
    }

    public String getAdditionalParams() {
        return additionalParams;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /**
     * BR-RDBMS-03: 更新時、パスワードを変更しない場合はencryptedPassword/encryptionKeyIdを
     * 呼び出し元が既存値のまま渡す（BR-RDBMS-12、空欄送信時は既存値を保持する処理はService層が担う）。
     */
    public void update(String displayName, DbType dbType, String host, int port, String databaseName,
                        String schemaName, String username, String encryptedPassword, int encryptionKeyId,
                        String additionalParams, Instant updatedAt) {
        this.displayName = displayName;
        this.dbType = dbType;
        this.host = host;
        this.port = port;
        this.databaseName = databaseName;
        this.schemaName = schemaName;
        this.username = username;
        this.encryptedPassword = encryptedPassword;
        this.encryptionKeyId = encryptionKeyId;
        this.additionalParams = additionalParams;
        this.updatedAt = updatedAt;
    }
}
