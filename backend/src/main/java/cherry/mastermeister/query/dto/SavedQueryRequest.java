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

package cherry.mastermeister.query.dto;

import cherry.mastermeister.query.entity.Visibility;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * frontend-components.md A-3・A-4。保存クエリの新規作成・更新で共通利用するリクエストボディ
 * （BR-QUERY-07: 編集可能な項目はSQL・名前・公開範囲のすべて）。
 */
public record SavedQueryRequest(
        @NotBlank String name,
        @NotBlank String sql,
        @NotNull Visibility visibility
) {
}
