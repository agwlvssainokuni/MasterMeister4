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
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * business-logic-model.md §7.2（PBT-01）。BR-MASTER-07。
 * オールオアナッシングの原子性を、実際のH2（インメモリ）に対して{@code DataSourceTransactionManager}
 * +{@code TransactionTemplate}経由で検証する。
 */
class RecordBatchServicePropertyTest {

    private static final AtomicInteger DB_COUNTER = new AtomicInteger();

    private static final List<RecordColumn> COLUMNS = List.of(
            new RecordColumn("id", ColumnDataTypeCategory.NUMERIC, true, true),
            new RecordColumn("name", ColumnDataTypeCategory.STRING, false, true));

    private final RecordBatchService service = new RecordBatchService();
    private final H2DialectStrategy dialect = new H2DialectStrategy();

    private DataSource newDataSource() {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("org.h2.Driver");
        // DriverManagerDataSourceは接続をプールせず呼び出しごとに新規接続するため、
        // DB_CLOSE_DELAY=-1がないと最初のCREATE TABLE後に接続が閉じた時点でDBごと破棄されてしまう。
        // ただしDB_CLOSE_DELAY=-1はJVM終了までDBを保持し続けるため、jqwikの試行回数(tries)を
        // 適度に絞り(このクラスでは30)、インメモリDBの蓄積によるヒープ圧迫を避ける
        ds.setUrl("jdbc:h2:mem:batch_prop_" + DB_COUNTER.incrementAndGet() + ";DB_CLOSE_DELAY=-1");
        ds.setUsername("sa");
        ds.setPassword("");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(ds);
        jdbcTemplate.execute("CREATE TABLE \"PUBLIC\".\"members\" (\"id\" INT PRIMARY KEY, "
                + "\"name\" VARCHAR(100) NOT NULL)");
        jdbcTemplate.update("INSERT INTO \"PUBLIC\".\"members\" VALUES (1, 'existing')");
        return ds;
    }

    private long countRows(DataSource dataSource) {
        Long count = new JdbcTemplate(dataSource)
                .queryForObject("SELECT COUNT(*) FROM \"PUBLIC\".\"members\"", Long.class);
        return count == null ? 0 : count;
    }

    @Provide
    Arbitrary<Integer> newId() {
        return Arbitraries.integers().between(100, 999);
    }

    /**
     * 原子性の不変条件: バッチ内に1件でもNOT NULL制約違反（nameがnull相当の欠落値）を含む場合、
     * 事前に成功していたはずの他の操作を含め、DB状態はバッチ実行前後で一切変化しない（0件反映）。
     */
    @Property(tries = 30)
    boolean atomicity_rollsBackAllOperations_whenAnyOperationViolatesConstraint(
            @ForAll("newId") int validId, @ForAll("newId") int invalidId) {
        if (validId == invalidId) {
            return true;
        }
        DataSource dataSource = newDataSource();
        long before = countRows(dataSource);

        var validCreate = new BatchOperationItem(OperationType.CREATE, null,
                Map.of("id", String.valueOf(validId), "name", "valid"));
        Map<String, String> invalidValues = new java.util.HashMap<>();
        invalidValues.put("id", String.valueOf(invalidId));
        // "name"を意図的に欠落させ、NOT NULL制約違反を発生させる
        var invalidCreate = new BatchOperationItem(OperationType.CREATE, null, invalidValues);
        List<BatchOperationItem> operations = new ArrayList<>(List.of(validCreate, invalidCreate));

        BatchOperationResult result = service.apply(dataSource, dialect, "PUBLIC", "members", COLUMNS, true, true,
                List.of("id"), operations);

        return !result.success() && countRows(dataSource) == before;
    }

    /**
     * 全件成功時の反映: バッチ内の全操作が有効な場合、全件が確実にDBへ反映される（部分適用は発生しない）。
     */
    @Property(tries = 30)
    boolean allSucceed_whenAllOperationsAreValid(@ForAll("newId") int idA, @ForAll("newId") int idB) {
        if (idA == idB) {
            return true;
        }
        DataSource dataSource = newDataSource();
        long before = countRows(dataSource);

        var createA = new BatchOperationItem(OperationType.CREATE, null,
                Map.of("id", String.valueOf(idA), "name", "a"));
        var createB = new BatchOperationItem(OperationType.CREATE, null,
                Map.of("id", String.valueOf(idB), "name", "b"));

        BatchOperationResult result = service.apply(dataSource, dialect, "PUBLIC", "members", COLUMNS, true, true,
                List.of("id"), List.of(createA, createB));

        return result.success() && countRows(dataSource) == before + 2;
    }
}
