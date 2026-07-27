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

package cherry.mastermeister.audit.dto;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * queryhistory.dto.QueryHistoryPageResponseと同様、Page/PageImplを直接レスポンスとして
 * 返さず独自の軽量なラッパーDTOに変換する。
 */
public record AuditLogPageResponse(
        List<AuditLogEntryResponse> content,
        int page,
        int pageSize,
        long totalElements,
        int totalPages
) {

    public static AuditLogPageResponse from(Page<AuditLogEntryResponse> page) {
        return new AuditLogPageResponse(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }
}
