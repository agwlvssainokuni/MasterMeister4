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

@Component
public class MariaDbDialectStrategy implements RdbmsDialectStrategy {

    @Override
    public DbType dbType() {
        return DbType.MARIADB;
    }

    @Override
    public boolean requiresSchemaSwitch() {
        // MariaDBもMySQL同様、「データベース」がスキーマに相当する単位として扱われるため切替不要
        return false;
    }

    @Override
    public void applySchemaSwitch(Connection connection, String schema) {
        throw new UnsupportedOperationException("MariaDB does not support schema switch");
    }

    @Override
    public String buildJdbcUrl(String host, int port, String databaseName, String additionalParams) {
        String base = "jdbc:mariadb://" + host + ":" + port + "/" + databaseName;
        return (additionalParams == null || additionalParams.isBlank()) ? base : base + "?" + additionalParams;
    }
}
