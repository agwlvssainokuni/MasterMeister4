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

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * domain-entities.md §1。タブUIでの指定内容全体。SQL生成API（{@code POST .../generate}）の
 * リクエストボディ。各リストの件数上限はtech-stack-decisions.md §5（承認前レビューで変更）のとおり。
 */
public record QueryBuilderStateRequest(
        @NotNull @Valid FromClauseDto from,
        @Size(max = 20) List<@Valid JoinClauseDto> joins,
        @NotEmpty @Size(max = 200) List<@Valid SelectItemDto> selectItems,
        @Size(max = 50) List<@Valid ConditionDto> whereConditions,
        @Size(max = 20) List<@Valid ColumnRefDto> groupByColumns,
        @Size(max = 20) List<@Valid ConditionDto> havingConditions,
        @Size(max = 20) List<@Valid OrderByItemDto> orderByItems,
        @PositiveOrZero Integer limit,
        @PositiveOrZero Integer offset
) {
}
