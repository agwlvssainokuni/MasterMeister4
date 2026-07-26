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

package cherry.mastermeister.query.api;

import cherry.mastermeister.query.QueryExecutionService;
import cherry.mastermeister.query.dto.AccessibleConnectionResponse;
import cherry.mastermeister.query.dto.AccessibleSchemaResponse;
import cherry.mastermeister.query.dto.QueryExecutionRequest;
import cherry.mastermeister.query.dto.QueryResultResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * frontend-components.md Flow A-1/B-1・B-2。logical-components.md §1。
 * 接続一覧・スキーマ一覧・ad-hoc実行（COMP-14 QueryExecutionServiceに対応）を担う。
 * 新規のSecurityFilterChainルールの要否はStep 8.5で確認する（UNIT-05の前例踏まえ）。
 */
@RestController
@RequestMapping("/api/queries")
public class QueryController {

    private final QueryExecutionService queryExecutionService;

    public QueryController(QueryExecutionService queryExecutionService) {
        this.queryExecutionService = queryExecutionService;
    }

    @GetMapping("/connections")
    public ResponseEntity<List<AccessibleConnectionResponse>> listConnections(
            @AuthenticationPrincipal Jwt principal) {
        List<AccessibleConnectionResponse> responses = queryExecutionService
                .listAccessibleConnections(currentUserId(principal)).stream()
                .map(AccessibleConnectionResponse::from)
                .toList();
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/{connectionId}/schemas")
    public ResponseEntity<List<AccessibleSchemaResponse>> listSchemas(@PathVariable Long connectionId,
                                                                       @AuthenticationPrincipal Jwt principal) {
        List<AccessibleSchemaResponse> responses = queryExecutionService
                .listAccessibleSchemas(currentUserId(principal), connectionId).stream()
                .map(AccessibleSchemaResponse::new)
                .toList();
        return ResponseEntity.ok(responses);
    }

    @PostMapping("/{connectionId}/execute")
    public ResponseEntity<QueryResultResponse> execute(@PathVariable Long connectionId,
                                                        @Valid @RequestBody QueryExecutionRequest request,
                                                        @AuthenticationPrincipal Jwt principal) {
        var result = queryExecutionService.execute(currentUserId(principal), connectionId, request.sql(),
                request.params() == null ? java.util.Map.of() : request.params(), request.schemaName(),
                request.pagingEnabled(), request.page(), request.pageSize());
        return ResponseEntity.ok(QueryResultResponse.from(result));
    }

    private Long currentUserId(Jwt principal) {
        return Long.valueOf(principal.getSubject());
    }
}
