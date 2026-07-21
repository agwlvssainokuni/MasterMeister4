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
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

@Component
public class H2DialectStrategy implements RdbmsDialectStrategy {

    @Override
    public DbType dbType() {
        return DbType.H2;
    }

    @Override
    public boolean requiresSchemaSwitch() {
        return true;
    }

    @Override
    public void applySchemaSwitch(Connection connection, String schema) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("SET SCHEMA \"" + schema.replace("\"", "\"\"") + "\"");
        }
    }

    @Override
    public String buildJdbcUrl(String host, int port, String databaseName, String schemaName,
                                String additionalParams) {
        // H2はTCPサーバモードを想定。パラメータ区切り文字は`;`（MySQL/PostgreSQL系の`&`とは異なる）
        String base = "jdbc:h2:tcp://" + host + ":" + port + "/" + databaseName;
        return (additionalParams == null || additionalParams.isBlank()) ? base : base + ";" + additionalParams;
    }
}
