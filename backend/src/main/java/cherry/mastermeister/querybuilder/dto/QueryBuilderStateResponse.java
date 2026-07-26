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

package cherry.mastermeister.querybuilder.dto;

import java.util.List;

/**
 * domain-entities.md §1。リバースエンジニアリングAPI（{@code POST .../parse}）のレスポンス。
 * {@link QueryBuilderStateRequest}と対称の構造（Part 1計画作成時の実装判断: 入れ子DTOは共用し
 * トップレベルのみRequest/Responseの2クラスに分ける）。
 */
public record QueryBuilderStateResponse(
        FromClauseDto from,
        List<JoinClauseDto> joins,
        List<SelectItemDto> selectItems,
        List<ConditionDto> whereConditions,
        List<ColumnRefDto> groupByColumns,
        List<ConditionDto> havingConditions,
        List<OrderByItemDto> orderByItems,
        Integer limit,
        Integer offset
) {
}
