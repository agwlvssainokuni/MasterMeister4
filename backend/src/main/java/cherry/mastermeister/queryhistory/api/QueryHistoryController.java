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

package cherry.mastermeister.queryhistory.api;

import cherry.mastermeister.common.exception.QueryHistoryInvalidParameterException;
import cherry.mastermeister.queryhistory.QueryHistoryService;
import cherry.mastermeister.queryhistory.dto.ExecutedByScope;
import cherry.mastermeister.queryhistory.dto.QueryHistoryConnectionResponse;
import cherry.mastermeister.queryhistory.dto.QueryHistoryPageResponse;
import cherry.mastermeister.queryhistory.dto.QueryHistorySearchCriteria;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/**
 * logical-components.md §1。クエリ履歴の閲覧・絞込を担う（COMP-17）。実行者スコープの
 * ロール判定を3エンドポイント共通で行い、Service層にはexecutedByFilter（絞込済みの実行者ID、
 * nullなら全ユーザ対象）のみを渡す（nfr-design-patterns.md §1.2、ロール判定ロジックを
 * Serviceに持ち込まない）。
 */
@RestController
@RequestMapping("/api/query-history")
public class QueryHistoryController {

    private static final int DEFAULT_PAGE_SIZE = 50;
    private static final int MAX_PAGE_SIZE = 200;
    private static final String ADMIN_ROLE = "ADMIN";

    private final QueryHistoryService queryHistoryService;

    public QueryHistoryController(QueryHistoryService queryHistoryService) {
        this.queryHistoryService = queryHistoryService;
    }

    @GetMapping("/connections")
    public ResponseEntity<List<QueryHistoryConnectionResponse>> listConnections(
            @RequestParam(defaultValue = "ALL") ExecutedByScope executedByScope,
            @AuthenticationPrincipal Jwt principal) {
        Long executedByFilter = resolveExecutedByFilter(principal, executedByScope);
        return ResponseEntity.ok(queryHistoryService.listConnections(executedByFilter));
    }

    @GetMapping("/{connectionId}/schemas")
    public ResponseEntity<List<String>> listSchemas(@PathVariable Long connectionId,
                                                      @RequestParam(defaultValue = "ALL") ExecutedByScope executedByScope,
                                                      @AuthenticationPrincipal Jwt principal) {
        Long executedByFilter = resolveExecutedByFilter(principal, executedByScope);
        return ResponseEntity.ok(queryHistoryService.listSchemas(connectionId, executedByFilter));
    }

    @GetMapping("/{connectionId}")
    public ResponseEntity<QueryHistoryPageResponse> listHistory(
            @PathVariable Long connectionId,
            @RequestParam(defaultValue = "ALL") ExecutedByScope executedByScope,
            @RequestParam(required = false) Instant executedAtFrom,
            @RequestParam(required = false) Instant executedAtTo,
            @RequestParam(required = false) String schemaName,
            @RequestParam(required = false) String sqlKeyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + DEFAULT_PAGE_SIZE) int pageSize,
            @AuthenticationPrincipal Jwt principal) {
        if (pageSize > MAX_PAGE_SIZE) {
            throw new QueryHistoryInvalidParameterException();
        }
        if (executedAtFrom != null && executedAtTo != null && executedAtFrom.isAfter(executedAtTo)) {
            throw new QueryHistoryInvalidParameterException();
        }

        Long executedByFilter = resolveExecutedByFilter(principal, executedByScope);
        QueryHistorySearchCriteria criteria = new QueryHistorySearchCriteria(executedAtFrom, executedAtTo,
                schemaName, sqlKeyword);
        Pageable pageable = PageRequest.of(page, pageSize, Sort.by(Sort.Direction.DESC, "executedAt"));

        var result = queryHistoryService.listHistory(connectionId, executedByFilter, criteria, pageable);
        return ResponseEntity.ok(QueryHistoryPageResponse.from(result));
    }

    /**
     * BR-QUERYHISTORY-03。一般ユーザはALLを指定してもMINEへ強制する（フェイルクローズ）。
     */
    private Long resolveExecutedByFilter(Jwt principal, ExecutedByScope scope) {
        boolean isAdmin = ADMIN_ROLE.equals(principal.getClaimAsString("role"));
        if (isAdmin && scope == ExecutedByScope.ALL) {
            return null;
        }
        return currentUserId(principal);
    }

    private Long currentUserId(Jwt principal) {
        return Long.valueOf(principal.getSubject());
    }
}
