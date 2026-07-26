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
import cherry.mastermeister.query.SavedQueryService;
import cherry.mastermeister.query.dto.QueryResultResponse;
import cherry.mastermeister.query.dto.SavedQueryExecutionRequest;
import cherry.mastermeister.query.dto.SavedQueryRequest;
import cherry.mastermeister.query.dto.SavedQuerySummaryResponse;
import cherry.mastermeister.query.entity.SavedQuery;
import cherry.mastermeister.query.model.VisibilityFilter;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * frontend-components.md Flow A-2/A-3/A-4。logical-components.md §1。
 * 保存クエリのCRUD・実行・非表示化（COMP-15 SavedQueryServiceに対応）を担う。
 */
@RestController
@RequestMapping("/api/queries/{connectionId}/saved")
public class SavedQueryController {

    private final SavedQueryService savedQueryService;
    private final QueryExecutionService queryExecutionService;

    public SavedQueryController(SavedQueryService savedQueryService, QueryExecutionService queryExecutionService) {
        this.savedQueryService = savedQueryService;
        this.queryExecutionService = queryExecutionService;
    }

    @GetMapping
    public ResponseEntity<List<SavedQuerySummaryResponse>> list(
            @PathVariable Long connectionId,
            @RequestParam(defaultValue = "ALL") VisibilityFilter visibility,
            @RequestParam(defaultValue = "false") boolean includeOwnRetired,
            @AuthenticationPrincipal Jwt principal) {
        Long userId = currentUserId(principal);
        List<SavedQuerySummaryResponse> responses = savedQueryService
                .listSavedQueries(userId, connectionId, visibility, includeOwnRetired).stream()
                .map(savedQuery -> SavedQuerySummaryResponse.from(savedQuery, userId))
                .toList();
        return ResponseEntity.ok(responses);
    }

    @PostMapping
    public ResponseEntity<SavedQuerySummaryResponse> create(@PathVariable Long connectionId,
                                                             @Valid @RequestBody SavedQueryRequest request,
                                                             @AuthenticationPrincipal Jwt principal) {
        Long userId = currentUserId(principal);
        SavedQuery saved = savedQueryService.saveQuery(userId, connectionId, request.name(), request.sql(),
                request.visibility());
        return ResponseEntity.ok(SavedQuerySummaryResponse.from(saved, userId));
    }

    @GetMapping("/{savedQueryId}")
    public ResponseEntity<SavedQuerySummaryResponse> get(@PathVariable Long connectionId,
                                                          @PathVariable Long savedQueryId,
                                                          @AuthenticationPrincipal Jwt principal) {
        Long userId = currentUserId(principal);
        SavedQuery savedQuery = savedQueryService.getSavedQuery(userId, connectionId, savedQueryId);
        return ResponseEntity.ok(SavedQuerySummaryResponse.from(savedQuery, userId));
    }

    @PutMapping("/{savedQueryId}")
    public ResponseEntity<SavedQuerySummaryResponse> update(@PathVariable Long connectionId,
                                                             @PathVariable Long savedQueryId,
                                                             @Valid @RequestBody SavedQueryRequest request,
                                                             @AuthenticationPrincipal Jwt principal) {
        Long userId = currentUserId(principal);
        SavedQuery updated = savedQueryService.updateQuery(userId, connectionId, savedQueryId, request.name(),
                request.sql(), request.visibility());
        return ResponseEntity.ok(SavedQuerySummaryResponse.from(updated, userId));
    }

    @PostMapping("/{savedQueryId}/execute")
    public ResponseEntity<QueryResultResponse> execute(@PathVariable Long connectionId,
                                                        @PathVariable Long savedQueryId,
                                                        @Valid @RequestBody SavedQueryExecutionRequest request,
                                                        @AuthenticationPrincipal Jwt principal) {
        var result = queryExecutionService.executeSavedQuery(currentUserId(principal), connectionId, savedQueryId,
                request.params() == null ? java.util.Map.of() : request.params(), request.schemaName(),
                request.pagingEnabled(), request.page(), request.pageSize());
        return ResponseEntity.ok(QueryResultResponse.from(result));
    }

    @PostMapping("/{savedQueryId}/retire")
    public ResponseEntity<Void> retire(@PathVariable Long connectionId, @PathVariable Long savedQueryId,
                                        @AuthenticationPrincipal Jwt principal) {
        savedQueryService.retireQuery(currentUserId(principal), connectionId, savedQueryId);
        return ResponseEntity.ok().build();
    }

    private Long currentUserId(Jwt principal) {
        return Long.valueOf(principal.getSubject());
    }
}
