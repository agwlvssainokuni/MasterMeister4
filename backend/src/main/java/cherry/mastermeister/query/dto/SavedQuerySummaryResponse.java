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

import cherry.mastermeister.query.entity.SavedQuery;
import cherry.mastermeister.query.entity.Visibility;

import java.time.Instant;

/**
 * frontend-components.md A-2・A-4。{@code own}は編集・非表示化ボタンの表示可否判定に使う
 * （BR-QUERY-07〜08、作成者のみ）。SQL本文は一覧では返さず、A-3/A-4個別取得（getSavedQuery）
 * でのみ返す想定だが、本レコードはCRUD全般で共通利用するため含める。
 */
public record SavedQuerySummaryResponse(
        Long id,
        String name,
        String sql,
        Visibility visibility,
        Long createdBy,
        boolean own,
        boolean retired,
        Instant createdAt,
        Instant updatedAt
) {

    public static SavedQuerySummaryResponse from(SavedQuery savedQuery, Long currentUserId) {
        return new SavedQuerySummaryResponse(savedQuery.getId(), savedQuery.getName(), savedQuery.getSql(),
                savedQuery.getVisibility(), savedQuery.getCreatedBy(),
                savedQuery.getCreatedBy().equals(currentUserId), savedQuery.isRetired(), savedQuery.getCreatedAt(),
                savedQuery.getUpdatedAt());
    }
}
