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
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RdbmsDialectStrategyTest {

    @Test
    void mySql_buildsUrlWithAmpersandSeparatedParams_andNoSchemaSwitch() {
        MySqlDialectStrategy strategy = new MySqlDialectStrategy();

        assertThat(strategy.dbType()).isEqualTo(DbType.MYSQL);
        assertThat(strategy.requiresSchemaSwitch()).isFalse();
        assertThat(strategy.buildJdbcUrl("localhost", 3306, "mastermeister", null, null))
                .isEqualTo("jdbc:mysql://localhost:3306/mastermeister");
        assertThat(strategy.buildJdbcUrl("localhost", 3306, "mastermeister", null, "useSSL=false&serverTimezone=UTC"))
                .isEqualTo("jdbc:mysql://localhost:3306/mastermeister?useSSL=false&serverTimezone=UTC");
        assertThatThrownBy(() -> strategy.applySchemaSwitch(null, "any"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void mariaDb_buildsUrlWithAmpersandSeparatedParams_andNoSchemaSwitch() {
        MariaDbDialectStrategy strategy = new MariaDbDialectStrategy();

        assertThat(strategy.dbType()).isEqualTo(DbType.MARIADB);
        assertThat(strategy.requiresSchemaSwitch()).isFalse();
        assertThat(strategy.buildJdbcUrl("localhost", 3307, "mastermeister", null, "useSSL=false"))
                .isEqualTo("jdbc:mariadb://localhost:3307/mastermeister?useSSL=false");
    }

    @Test
    void postgres_buildsUrlWithAmpersandSeparatedParams_andRequiresSchemaSwitch() {
        PostgresDialectStrategy strategy = new PostgresDialectStrategy();

        assertThat(strategy.dbType()).isEqualTo(DbType.POSTGRESQL);
        assertThat(strategy.requiresSchemaSwitch()).isTrue();
        // schemaNameはURLに含めない（applySchemaSwitch経由でSET search_pathを適用する）
        assertThat(strategy.buildJdbcUrl("localhost", 5432, "mastermeister", "public", "sslmode=require"))
                .isEqualTo("jdbc:postgresql://localhost:5432/mastermeister?sslmode=require");
    }

    @Test
    void h2_buildsUrlWithSemicolonSeparatedParams_andRequiresSchemaSwitch() {
        H2DialectStrategy strategy = new H2DialectStrategy();

        assertThat(strategy.dbType()).isEqualTo(DbType.H2);
        assertThat(strategy.requiresSchemaSwitch()).isTrue();
        assertThat(strategy.buildJdbcUrl("localhost", 9092, "mem:testdb", null, "DB_CLOSE_DELAY=-1"))
                .isEqualTo("jdbc:h2:tcp://localhost:9092/mem:testdb;DB_CLOSE_DELAY=-1");
        assertThat(strategy.buildJdbcUrl("localhost", 9092, "mem:testdb", null, null))
                .isEqualTo("jdbc:h2:tcp://localhost:9092/mem:testdb");
    }

    @Test
    void resolver_resolvesEachDbTypeToItsMatchingStrategy() {
        MySqlDialectStrategy mysql = new MySqlDialectStrategy();
        MariaDbDialectStrategy mariadb = new MariaDbDialectStrategy();
        PostgresDialectStrategy postgres = new PostgresDialectStrategy();
        H2DialectStrategy h2 = new H2DialectStrategy();
        RdbmsDialectStrategyResolver resolver = new RdbmsDialectStrategyResolver(
                List.of(mysql, mariadb, postgres, h2));

        assertThat(resolver.resolve(DbType.MYSQL)).isSameAs(mysql);
        assertThat(resolver.resolve(DbType.MARIADB)).isSameAs(mariadb);
        assertThat(resolver.resolve(DbType.POSTGRESQL)).isSameAs(postgres);
        assertThat(resolver.resolve(DbType.H2)).isSameAs(h2);
    }

    @Test
    void resolver_throws_whenDialectNotRegistered() {
        RdbmsDialectStrategyResolver resolver = new RdbmsDialectStrategyResolver(List.of(new MySqlDialectStrategy()));

        assertThatThrownBy(() -> resolver.resolve(DbType.POSTGRESQL)).isInstanceOf(IllegalStateException.class);
    }
}
