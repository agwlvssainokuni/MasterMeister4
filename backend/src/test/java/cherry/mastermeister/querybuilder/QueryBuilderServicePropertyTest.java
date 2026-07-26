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

package cherry.mastermeister.querybuilder;

import cherry.mastermeister.querybuilder.dto.AccessibleBuilderColumnResponse;
import cherry.mastermeister.querybuilder.dto.AccessibleBuilderTableResponse;
import cherry.mastermeister.querybuilder.dto.ColumnDataTypeCategory;
import cherry.mastermeister.querybuilder.dto.ColumnRefDto;
import cherry.mastermeister.querybuilder.dto.ConditionDto;
import cherry.mastermeister.querybuilder.dto.ConditionOperator;
import cherry.mastermeister.querybuilder.dto.FromClauseDto;
import cherry.mastermeister.querybuilder.dto.QueryBuilderStateRequest;
import cherry.mastermeister.querybuilder.dto.QueryBuilderStateResponse;
import cherry.mastermeister.querybuilder.dto.SelectItemDto;
import cherry.mastermeister.rdbmsconnection.entity.TableType;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * business-logic-model.md §8（PBT対象、STORY-5.2）。{@code generateSql}→{@code parseToBuilderState}の
 * ラウンドトリップが元の状態と構造的に等価であることを検証する。{@link QueryBuilderAccessResolver}は
 * Mockでスタブ化し常にアクセス可能として扱う（nfr-requirements.md、実際のスキーマ・権限判定への
 * 依存を切り離すため）。固定の単一テーブル（{@code items}、列: {@code id}=NUMERIC, {@code name}=STRING,
 * {@code amount}=NUMERIC）を対象に、SELECT列・WHERE条件の組み合わせを変化させる。
 */
class QueryBuilderServicePropertyTest {

    private static final Long USER_ID = 1L;
    private static final Long CONNECTION_ID = 100L;
    private static final String SCHEMA = "public";
    private static final String ALIAS = "t1";

    private QueryBuilderService newService() {
        QueryBuilderAccessResolver accessResolver = mock(QueryBuilderAccessResolver.class);
        when(accessResolver.isColumnAccessible(any(), any(), anyString(), anyString(), anyString())).thenReturn(true);
        when(accessResolver.listAccessibleTables(USER_ID, CONNECTION_ID, SCHEMA)).thenReturn(List.of(
                new AccessibleBuilderTableResponse("items", TableType.TABLE, List.of(
                        new AccessibleBuilderColumnResponse("id", ColumnDataTypeCategory.NUMERIC),
                        new AccessibleBuilderColumnResponse("name", ColumnDataTypeCategory.STRING),
                        new AccessibleBuilderColumnResponse("amount", ColumnDataTypeCategory.NUMERIC)))));
        return new QueryBuilderService(accessResolver);
    }

    @Provide
    Arbitrary<String> selectableColumn() {
        return Arbitraries.of("id", "name", "amount");
    }

    @Provide
    Arbitrary<List<String>> selectColumns() {
        return selectableColumn().list().ofMinSize(1).ofMaxSize(3).uniqueElements();
    }

    @Provide
    Arbitrary<Long> numericLiteral() {
        return Arbitraries.longs().between(0L, 100_000L);
    }

    /**
     * ラウンドトリップ性質: SELECT列の組み合わせ＋WHERE条件（amount列との数値比較）を変化させても、
     * generateSql→parseToBuilderStateで得られる状態が元の状態と構造的に一致する。
     */
    @Property
    boolean generateSqlThenParse_roundTripsToEquivalentState(
            @ForAll("selectColumns") List<String> columns, @ForAll("numericLiteral") Long threshold) {
        QueryBuilderService service = newService();

        List<SelectItemDto> selectItems = columns.stream()
                .map(name -> new SelectItemDto(new ColumnRefDto(ALIAS, name), null, null))
                .toList();
        ConditionDto whereCondition = new ConditionDto(new ColumnRefDto(ALIAS, "amount"), null, ConditionOperator.GT,
                String.valueOf(threshold), ColumnDataTypeCategory.NUMERIC);
        QueryBuilderStateRequest request = new QueryBuilderStateRequest(new FromClauseDto(SCHEMA, "items", ALIAS),
                List.of(), selectItems, List.of(whereCondition), List.of(), List.of(), List.of(), null, null);

        String sql = service.generateSql(request);
        QueryBuilderStateResponse response = service.parseToBuilderState(USER_ID, CONNECTION_ID, SCHEMA, sql);

        return response.from().equals(request.from())
                && response.selectItems().equals(request.selectItems())
                && response.whereConditions().equals(request.whereConditions())
                && response.joins().isEmpty()
                && response.groupByColumns().isEmpty();
    }
}
