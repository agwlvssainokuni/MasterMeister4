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

import cherry.mastermeister.masterdata.model.BatchOperationItem;
import cherry.mastermeister.masterdata.model.BatchOperationResult;
import cherry.mastermeister.masterdata.model.ColumnDataTypeCategory;
import cherry.mastermeister.masterdata.model.OperationType;
import cherry.mastermeister.masterdata.model.RecordColumn;
import cherry.mastermeister.rdbmsconnection.dialect.H2DialectStrategy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * logical-components.md §1。business-rules.md BR-MASTER-06〜09。
 * 実際にH2（インメモリ、実テーブル）へトランザクション制御込みで一括反映を実行して検証する
 * （Step 3.5の検証チェックポイント）。特にBR-MASTER-07のオールオアナッシング
 * （{@code DataSourceTransactionManager}+{@code TransactionTemplate}による制御）を重点確認する。
 */
class RecordBatchServiceTest {

    private final RecordBatchService service = new RecordBatchService();
    private final H2DialectStrategy dialect = new H2DialectStrategy();
    private DataSource dataSource;

    // idはBR-ACCESS-06によりcanCreate()がtrueとなる前提として実効主権限UPDATE
    // （editable=true）を持つ（主キー列を含む全主キー列がUPDATEでなければcanCreate()はfalseになるため、
    // creatable=trueのテストケースではeditable=trueが実際の権限解決結果と整合する）
    private static final List<RecordColumn> COLUMNS = List.of(
            new RecordColumn("id", ColumnDataTypeCategory.NUMERIC, true, true),
            new RecordColumn("name", ColumnDataTypeCategory.STRING, false, true),
            new RecordColumn("amount", ColumnDataTypeCategory.NUMERIC, false, true));

    @BeforeEach
    void setUp() {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("org.h2.Driver");
        ds.setUrl("jdbc:h2:mem:record_batch_test;DB_CLOSE_DELAY=-1");
        ds.setUsername("sa");
        ds.setPassword("");
        dataSource = ds;

        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS \"PUBLIC\".\"items\" (\"id\" INT PRIMARY KEY, "
                + "\"name\" VARCHAR(100) NOT NULL, \"amount\" DECIMAL(10,2))");
        jdbcTemplate.execute("DELETE FROM \"PUBLIC\".\"items\"");
        jdbcTemplate.update("INSERT INTO \"PUBLIC\".\"items\" VALUES (1, 'Widget', 10.00)");
        jdbcTemplate.update("INSERT INTO \"PUBLIC\".\"items\" VALUES (2, 'Gadget', 20.00)");
    }

    @AfterEach
    void tearDown() {
        new JdbcTemplate(dataSource).execute("DROP TABLE \"PUBLIC\".\"items\"");
    }

    private long countRows() {
        Long count = new JdbcTemplate(dataSource).queryForObject("SELECT COUNT(*) FROM \"PUBLIC\".\"items\"",
                Long.class);
        return count == null ? 0 : count;
    }

    @Test
    void apply_commitsAllOperations_whenAllSucceed() {
        var create = new BatchOperationItem(OperationType.CREATE, null,
                Map.of("id", "3", "name", "Thingamajig", "amount", "30.00"));
        var update = new BatchOperationItem(OperationType.UPDATE, Map.of("id", "1"),
                Map.of("amount", "99.00"));
        var delete = new BatchOperationItem(OperationType.DELETE, Map.of("id", "2"), null);

        BatchOperationResult result = service.apply(dataSource, dialect, "PUBLIC", "items", COLUMNS, true, true,
                List.of("id"), List.of(create, update, delete));

        assertThat(result.success()).isTrue();
        assertThat(countRows()).isEqualTo(2);
        Map<String, Object> updated = new JdbcTemplate(dataSource)
                .queryForMap("SELECT * FROM \"PUBLIC\".\"items\" WHERE \"id\" = 1");
        assertThat(((Number) updated.get("amount")).doubleValue()).isEqualTo(99.00);
    }

    @Test
    void apply_rollsBackEverything_whenOneOperationViolatesConstraint() {
        // 2件目(id=2)がNOT NULL制約違反(nameがnull) -> オールオアナッシングで1件目も反映されないこと
        var validCreate = new BatchOperationItem(OperationType.CREATE, null,
                Map.of("id", "3", "name", "Thingamajig", "amount", "30.00"));
        Map<String, String> invalidValues = new java.util.HashMap<>();
        invalidValues.put("id", "4");
        invalidValues.put("amount", "10.00");
        var invalidCreate = new BatchOperationItem(OperationType.CREATE, null, invalidValues);

        BatchOperationResult result = service.apply(dataSource, dialect, "PUBLIC", "items", COLUMNS, true, true,
                List.of("id"), List.of(validCreate, invalidCreate));

        assertThat(result.success()).isFalse();
        assertThat(result.itemResults()).hasSize(1);
        assertThat(result.itemResults().get(0).index()).isEqualTo(1);
        assertThat(result.itemResults().get(0).errorCode()).isEqualTo("CONSTRAINT_VIOLATION");
        // ロールバックにより、先に成功していたはずのvalidCreateもDBに反映されていないこと(原子性の確認)
        assertThat(countRows()).isEqualTo(2);
    }

    @Test
    void apply_rejectsCreate_whenNotCreatable() {
        var create = new BatchOperationItem(OperationType.CREATE, null,
                Map.of("id", "3", "name", "Thingamajig", "amount", "30.00"));

        BatchOperationResult result = service.apply(dataSource, dialect, "PUBLIC", "items", COLUMNS, false, true,
                List.of("id"), List.of(create));

        assertThat(result.success()).isFalse();
        assertThat(result.itemResults().get(0).errorCode()).isEqualTo("PERMISSION_DENIED");
        assertThat(countRows()).isEqualTo(2);
    }

    @Test
    void apply_rejectsUpdate_whenColumnNotEditable() {
        List<RecordColumn> readOnlyAmount = List.of(
                new RecordColumn("id", ColumnDataTypeCategory.NUMERIC, true, false),
                new RecordColumn("name", ColumnDataTypeCategory.STRING, false, true),
                new RecordColumn("amount", ColumnDataTypeCategory.NUMERIC, false, false));
        var update = new BatchOperationItem(OperationType.UPDATE, Map.of("id", "1"), Map.of("amount", "50.00"));

        BatchOperationResult result = service.apply(dataSource, dialect, "PUBLIC", "items", readOnlyAmount, true,
                true, List.of("id"), List.of(update));

        assertThat(result.success()).isFalse();
        assertThat(result.itemResults().get(0).errorCode()).isEqualTo("PERMISSION_DENIED");
    }

    @Test
    void apply_rejectsDelete_whenNotDeletable() {
        var delete = new BatchOperationItem(OperationType.DELETE, Map.of("id", "1"), null);

        BatchOperationResult result = service.apply(dataSource, dialect, "PUBLIC", "items", COLUMNS, true, false,
                List.of("id"), List.of(delete));

        assertThat(result.success()).isFalse();
        assertThat(result.itemResults().get(0).errorCode()).isEqualTo("PERMISSION_DENIED");
        assertThat(countRows()).isEqualTo(2);
    }

    @Test
    void apply_rejectsUpdateDelete_whenPrimaryKeyMissing() {
        var update = new BatchOperationItem(OperationType.UPDATE, Map.of(), Map.of("amount", "50.00"));

        BatchOperationResult result = service.apply(dataSource, dialect, "PUBLIC", "items", COLUMNS, true, true,
                List.of("id"), List.of(update));

        assertThat(result.success()).isFalse();
        assertThat(result.itemResults().get(0).errorCode()).isEqualTo("INVALID_VALUE");
    }

    @Test
    void apply_reportsRecordNotFound_whenPrimaryKeyDoesNotMatchAnyRow() {
        var update = new BatchOperationItem(OperationType.UPDATE, Map.of("id", "999"),
                Map.of("amount", "50.00"));

        BatchOperationResult result = service.apply(dataSource, dialect, "PUBLIC", "items", COLUMNS, true, true,
                List.of("id"), List.of(update));

        assertThat(result.success()).isFalse();
        assertThat(result.itemResults().get(0).errorCode()).isEqualTo("RECORD_NOT_FOUND");
    }
}
