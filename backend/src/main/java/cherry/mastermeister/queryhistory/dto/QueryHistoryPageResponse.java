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

package cherry.mastermeister.queryhistory.dto;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * 実装時の判断: Spring Data JPAのPage/PageImplを直接レスポンスとして返さず、
 * masterdata.dto.RecordPageResponseと同様の独自の軽量なラッパーDTOに変換する
 * （PageImplの標準シリアライズ形式はJSON構造が肥大化しやすく非推奨のため）。
 */
public record QueryHistoryPageResponse(
        List<QueryHistoryRecordResponse> content,
        int page,
        int pageSize,
        long totalElements,
        int totalPages
) {

    public static QueryHistoryPageResponse from(Page<QueryHistoryRecordResponse> page) {
        return new QueryHistoryPageResponse(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }
}
