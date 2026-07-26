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

package cherry.mastermeister.querybuilder.api;

import cherry.mastermeister.querybuilder.QueryBuilderAccessResolver;
import cherry.mastermeister.querybuilder.QueryBuilderService;
import cherry.mastermeister.querybuilder.dto.AccessibleBuilderTableResponse;
import cherry.mastermeister.querybuilder.dto.GenerateSqlResponse;
import cherry.mastermeister.querybuilder.dto.ParseSqlRequest;
import cherry.mastermeister.querybuilder.dto.QueryBuilderStateRequest;
import cherry.mastermeister.querybuilder.dto.QueryBuilderStateResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * frontend-components.md 画面2。nfr-design/logical-components.md §1（Q5=A）。
 * 接続一覧・スキーマ一覧はUNIT-06の既存エンドポイント（{@code /api/queries/connections}・
 * {@code /api/queries/{connectionId}/schemas}）を再利用するため本Controllerには含めない。
 * 既存の{@code SecurityConfig}の{@code /api/**}.authenticated()ルールでカバーされるため、
 * 新規のSecurityFilterChainルールは不要（nfr-design-patterns.md §4）。
 */
@RestController
@RequestMapping("/api/query-builder")
public class QueryBuilderController {

    private final QueryBuilderAccessResolver accessResolver;
    private final QueryBuilderService queryBuilderService;

    public QueryBuilderController(QueryBuilderAccessResolver accessResolver,
                                   QueryBuilderService queryBuilderService) {
        this.accessResolver = accessResolver;
        this.queryBuilderService = queryBuilderService;
    }

    @GetMapping("/{connectionId}/tables")
    public ResponseEntity<List<AccessibleBuilderTableResponse>> listTables(@PathVariable Long connectionId,
                                                                            @RequestParam String schemaName,
                                                                            @AuthenticationPrincipal Jwt principal) {
        return ResponseEntity.ok(
                accessResolver.listAccessibleTables(currentUserId(principal), connectionId, schemaName));
    }

    @PostMapping("/{connectionId}/generate")
    public ResponseEntity<GenerateSqlResponse> generate(@PathVariable Long connectionId,
                                                         @Valid @RequestBody QueryBuilderStateRequest request,
                                                         @AuthenticationPrincipal Jwt principal) {
        String sql = queryBuilderService.generateSql(request);
        return ResponseEntity.ok(new GenerateSqlResponse(sql));
    }

    @PostMapping("/{connectionId}/parse")
    public ResponseEntity<QueryBuilderStateResponse> parse(@PathVariable Long connectionId,
                                                            @Valid @RequestBody ParseSqlRequest request,
                                                            @AuthenticationPrincipal Jwt principal) {
        QueryBuilderStateResponse response = queryBuilderService.parseToBuilderState(currentUserId(principal),
                connectionId, request.schemaName(), request.sql());
        return ResponseEntity.ok(response);
    }

    private Long currentUserId(Jwt principal) {
        return Long.valueOf(principal.getSubject());
    }
}
