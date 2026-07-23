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

package cherry.mastermeister.permission.entity;

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
 * domain-entities.md §1。BR-ACCESS-01〜03。
 * tableName/columnNameは「該当階層なし」を表す場合、永続化上は空文字列（センチネル値）で
 * 保持する（NULL同士は複合UNIQUE制約上「等しくない」とみなされ一意性が機能しなくなるため、
 * nfr-design-patterns.md §3.1）。本クラスのgetter/コンストラクタはこの変換を吸収し、
 * 呼び出し側（Service層以上）からは「未設定はnull」という一貫したモデルを保つ。
 */
@Entity
@Table(name = "access_permission")
public class AccessPermission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "connection_id", nullable = false)
    private Long connectionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "principal_type", nullable = false, length = 10)
    private PrincipalType principalType;

    @Column(name = "principal_id", nullable = false)
    private Long principalId;

    @Column(name = "schema_name", nullable = false)
    private String schemaName;

    @Column(name = "table_name", nullable = false)
    private String tableNameRaw;

    @Column(name = "column_name", nullable = false)
    private String columnNameRaw;

    @Enumerated(EnumType.STRING)
    @Column(name = "primary_permission", nullable = false, length = 10)
    private PrimaryPermission primaryPermission;

    @Column(name = "create_permission", nullable = false)
    private boolean createPermission;

    @Column(name = "delete_permission", nullable = false)
    private boolean deletePermission;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false)
    private Long updatedBy;

    protected AccessPermission() {
        // JPA
    }

    public AccessPermission(Long connectionId, PrincipalType principalType, Long principalId, String schemaName,
                             String tableName, String columnName, PrimaryPermission primaryPermission,
                             boolean createPermission, boolean deletePermission, Instant updatedAt, Long updatedBy) {
        this.connectionId = connectionId;
        this.principalType = principalType;
        this.principalId = principalId;
        this.schemaName = schemaName;
        this.tableNameRaw = toSentinel(tableName);
        this.columnNameRaw = toSentinel(columnName);
        this.primaryPermission = primaryPermission;
        this.createPermission = createPermission;
        this.deletePermission = deletePermission;
        this.updatedAt = updatedAt;
        this.updatedBy = updatedBy;
    }

    public Long getId() {
        return id;
    }

    public Long getConnectionId() {
        return connectionId;
    }

    public PrincipalType getPrincipalType() {
        return principalType;
    }

    public Long getPrincipalId() {
        return principalId;
    }

    public String getSchemaName() {
        return schemaName;
    }

    public String getTableName() {
        return denullify(tableNameRaw);
    }

    public String getColumnName() {
        return denullify(columnNameRaw);
    }

    public PrimaryPermission getPrimaryPermission() {
        return primaryPermission;
    }

    public boolean isCreatePermission() {
        return createPermission;
    }

    public boolean isDeletePermission() {
        return deletePermission;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Long getUpdatedBy() {
        return updatedBy;
    }

    /**
     * BR-ACCESS-01: 主権限・補助権限の変更。カラム階層（columnNameが設定されている行）では
     * 補助権限は常にfalseとして扱う（FR-2.5）。
     */
    public void updatePermission(PrimaryPermission primaryPermission, boolean createPermission,
                                  boolean deletePermission, Instant updatedAt, Long updatedBy) {
        boolean isColumnLevel = getColumnName() != null;
        this.primaryPermission = primaryPermission;
        this.createPermission = isColumnLevel ? false : createPermission;
        this.deletePermission = isColumnLevel ? false : deletePermission;
        this.updatedAt = updatedAt;
        this.updatedBy = updatedBy;
    }

    /**
     * リポジトリの検索条件構築（upsert対象検索）でも使用するためpublicとする。
     */
    public static String toSentinel(String value) {
        return value == null ? "" : value;
    }

    private static String denullify(String rawValue) {
        return rawValue.isEmpty() ? null : rawValue;
    }
}
