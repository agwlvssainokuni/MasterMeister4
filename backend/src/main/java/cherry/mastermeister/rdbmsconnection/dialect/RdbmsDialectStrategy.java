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

package cherry.mastermeister.rdbmsconnection.dialect;

import cherry.mastermeister.rdbmsconnection.entity.DbType;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * COMP-09。MySQL/MariaDB/PostgreSQL/H2の方言差を吸収する（Strategy/Adapterパターン）。
 * 実際の方言解決は{@link RdbmsDialectStrategyResolver}が行う
 * （component-methods.mdの"resolveDialect(dbType): RdbmsDialectStrategy ファクトリメソッド"を、
 * Spring管理下の複数実装をDIで受け取れる専用リゾルバコンポーネントとして実装したもの）。
 */
public interface RdbmsDialectStrategy {

    DbType dbType();

    /**
     * このDB種別がスキーマ切替の概念を持つか（nfr-design/logical-components.md §1）。
     */
    boolean requiresSchemaSwitch();

    /**
     * スキーマ切替を適用する。{@link #requiresSchemaSwitch()}が{@code false}の方言では呼び出さない。
     */
    void applySchemaSwitch(Connection connection, String schema) throws SQLException;

    /**
     * JDBC URLを構築する（レビュー指摘の反映、nfr-design/logical-components.md §1、
     * component-methods.md訂正）。スキーム・パラメータ区切り文字は方言ごとに異なるため、
     * URL構築自体を本メソッドに集約する。
     */
    String buildJdbcUrl(String host, int port, String databaseName, String schemaName, String additionalParams);
}
