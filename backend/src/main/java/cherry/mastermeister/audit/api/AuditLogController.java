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

package cherry.mastermeister.audit.api;

import cherry.mastermeister.audit.AuditLogQueryService;
import cherry.mastermeister.audit.dto.AuditLogPageResponse;
import cherry.mastermeister.audit.dto.AuditLogSearchCriteria;
import cherry.mastermeister.audit.entity.AuditEventType;
import cherry.mastermeister.audit.entity.ResultStatus;
import cherry.mastermeister.common.exception.AuditLogInvalidParameterException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * logical-components.md §1。監査ログの閲覧・絞込を担う（COMP-18）。全エンドポイントとも、
 * 既存のSecurityFilterChain設定（{@code /api/admin/**}）により管理者ロール必須
 * （nfr-design-patterns.md §1.2、追加設定不要）。ロール判定・実行者スコープのフィルタ変換は
 * 行わない（本ユニットは管理者専用であり、UNIT-08のexecutedByFilterのような仕組みは不要）。
 */
@RestController
@RequestMapping("/api/admin/audit-log")
public class AuditLogController {

    private static final int DEFAULT_PAGE_SIZE = 50;
    private static final int MAX_PAGE_SIZE = 200;

    private final AuditLogQueryService auditLogQueryService;

    public AuditLogController(AuditLogQueryService auditLogQueryService) {
        this.auditLogQueryService = auditLogQueryService;
    }

    @GetMapping
    public ResponseEntity<AuditLogPageResponse> listAuditLog(
            @RequestParam(required = false) Instant occurredAtFrom,
            @RequestParam(required = false) Instant occurredAtTo,
            @RequestParam(required = false) AuditEventType eventType,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) Long connectionId,
            @RequestParam(required = false) ResultStatus resultStatus,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + DEFAULT_PAGE_SIZE) int pageSize) {
        if (pageSize > MAX_PAGE_SIZE) {
            throw new AuditLogInvalidParameterException();
        }
        if (occurredAtFrom != null && occurredAtTo != null && occurredAtFrom.isAfter(occurredAtTo)) {
            throw new AuditLogInvalidParameterException();
        }

        AuditLogSearchCriteria criteria = new AuditLogSearchCriteria(occurredAtFrom, occurredAtTo, eventType, userId,
                connectionId, resultStatus);
        Pageable pageable = PageRequest.of(page, pageSize, Sort.by(Sort.Direction.DESC, "occurredAt"));

        var result = auditLogQueryService.listAuditLog(criteria, pageable);
        return ResponseEntity.ok(AuditLogPageResponse.from(result));
    }
}
