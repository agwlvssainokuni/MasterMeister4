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

package cherry.mastermeister.masterdata;

import cherry.mastermeister.common.exception.InvalidQueryConditionException;
import cherry.mastermeister.masterdata.model.ColumnDataTypeCategory;
import cherry.mastermeister.masterdata.model.FilterOperator;
import cherry.mastermeister.masterdata.model.RecordColumn;
import cherry.mastermeister.masterdata.model.RecordFilterCondition;
import cherry.mastermeister.masterdata.model.RecordPage;
import cherry.mastermeister.rdbmsconnection.dialect.H2DialectStrategy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * logical-components.md §1。business-logic-model.md §3。実際にH2（インメモリ、実テーブル）へ
 * SELECT/COUNTを発行して検証する（Step 3.5の検証チェックポイント）。
 */
class RecordQueryServiceTest {

    private final RecordQueryService service = new RecordQueryService();
    private final H2DialectStrategy dialect = new H2DialectStrategy();
    private DataSource dataSource;

    private static final List<RecordColumn> COLUMNS = List.of(
            new RecordColumn("id", ColumnDataTypeCategory.NUMERIC, true, false),
            new RecordColumn("name", ColumnDataTypeCategory.STRING, false, true),
            new RecordColumn("active", ColumnDataTypeCategory.BOOLEAN, false, true));

    @BeforeEach
    void setUp() {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("org.h2.Driver");
        ds.setUrl("jdbc:h2:mem:record_query_test;DB_CLOSE_DELAY=-1");
        ds.setUsername("sa");
        ds.setPassword("");
        dataSource = ds;

        // 識別子はダブルクオートで作成し、quoteIdentifierによるクオート付与後の値と大文字/小文字を
        // 一致させる（テーブル/カラム名は小文字、既定スキーマ名はH2の実際の格納値であるPUBLIC）。
        // 実運用ではUNIT-03のDatabaseMetaData由来の値がそのまま渡されるため同様の整合性が保たれる
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS \"PUBLIC\".\"members\" (\"id\" INT PRIMARY KEY, "
                + "\"name\" VARCHAR(100), \"active\" BOOLEAN)");
        jdbcTemplate.execute("DELETE FROM \"PUBLIC\".\"members\"");
        jdbcTemplate.update("INSERT INTO \"PUBLIC\".\"members\" VALUES (1, 'Alice', true)");
        jdbcTemplate.update("INSERT INTO \"PUBLIC\".\"members\" VALUES (2, 'Bob', false)");
        jdbcTemplate.update("INSERT INTO \"PUBLIC\".\"members\" VALUES (3, 'Charlie', true)");
    }

    @AfterEach
    void tearDown() {
        new JdbcTemplate(dataSource).execute("DROP TABLE \"PUBLIC\".\"members\"");
    }

    @Test
    void queryRecords_returnsAllRows_withDefaultSortByPrimaryKey() {
        RecordPage page = service.queryRecords(dataSource, dialect, "PUBLIC", "members", COLUMNS, List.of("id"),
                List.of(), RawQueryConditionValidator.Validated.EMPTY, 0, 10, true, true);

        assertThat(page.totalCount()).isEqualTo(3);
        assertThat(page.rows()).hasSize(3);
        assertThat(page.rows().get(0)).containsEntry("name", "Alice");
        assertThat(page.creatable()).isTrue();
        assertThat(page.deletable()).isTrue();
    }

    @Test
    void queryRecords_appliesStructuredFilter() {
        var filter = new RecordFilterCondition("active", FilterOperator.EQ, "true", null);
        RecordPage page = service.queryRecords(dataSource, dialect, "PUBLIC", "members", COLUMNS, List.of("id"),
                List.of(filter), RawQueryConditionValidator.Validated.EMPTY, 0, 10, true, true);

        assertThat(page.totalCount()).isEqualTo(2);
        assertThat(page.rows()).extracting(r -> r.get("name")).containsExactlyInAnyOrder("Alice", "Charlie");
    }

    @Test
    void queryRecords_appliesContainsFilter_withLikeEscaping() {
        var filter = new RecordFilterCondition("name", FilterOperator.CONTAINS, "li", null);
        RecordPage page = service.queryRecords(dataSource, dialect, "PUBLIC", "members", COLUMNS, List.of("id"),
                List.of(filter), RawQueryConditionValidator.Validated.EMPTY, 0, 10, true, true);

        assertThat(page.rows()).extracting(r -> r.get("name")).containsExactlyInAnyOrder("Alice", "Charlie");
    }

    @Test
    void queryRecords_pagesResults() {
        RecordPage page = service.queryRecords(dataSource, dialect, "PUBLIC", "members", COLUMNS, List.of("id"),
                List.of(), RawQueryConditionValidator.Validated.EMPTY, 1, 2, true, true);

        assertThat(page.totalCount()).isEqualTo(3);
        assertThat(page.rows()).hasSize(1);
        assertThat(page.rows().get(0)).containsEntry("name", "Charlie");
    }

    @Test
    void queryRecords_combinesStructuredFilterAndRawWhere_withAnd() {
        var filter = new RecordFilterCondition("active", FilterOperator.EQ, "true", null);
        var rawQuery = new RawQueryConditionValidator().validate(dialect, "id > 1", null);

        RecordPage page = service.queryRecords(dataSource, dialect, "PUBLIC", "members", COLUMNS, List.of("id"),
                List.of(filter), rawQuery, 0, 10, true, true);

        assertThat(page.rows()).extracting(r -> r.get("name")).containsExactly("Charlie");
    }

    @Test
    void queryRecords_excludesHiddenColumns_fromSelectAndResponse() {
        List<RecordColumn> visibleOnly = List.of(new RecordColumn("name", ColumnDataTypeCategory.STRING, false,
                true));
        RecordPage page = service.queryRecords(dataSource, dialect, "PUBLIC", "members", visibleOnly, List.of("id"),
                List.of(), RawQueryConditionValidator.Validated.EMPTY, 0, 10, true, true);

        assertThat(page.rows().get(0)).containsOnlyKeys("name");
    }

    @Test
    void queryRecords_throwsInvalidQueryCondition_whenFilterReferencesUnknownColumn() {
        var filter = new RecordFilterCondition("does_not_exist", FilterOperator.EQ, "x", null);

        assertThatThrownBy(() -> service.queryRecords(dataSource, dialect, "PUBLIC", "members", COLUMNS,
                List.of("id"), List.of(filter), RawQueryConditionValidator.Validated.EMPTY, 0, 10, true, true))
                .isInstanceOf(InvalidQueryConditionException.class);
    }

    @Test
    void queryRecords_rawOrderByTakesPrecedenceOverDefault() {
        var rawQuery = new RawQueryConditionValidator().validate(dialect, null, "id DESC");
        RecordPage page = service.queryRecords(dataSource, dialect, "PUBLIC", "members", COLUMNS, List.of("id"),
                List.of(), rawQuery, 0, 10, true, true);

        assertThat(page.rows()).extracting(r -> r.get("name"))
                .containsExactly("Charlie", "Bob", "Alice");
    }
}
