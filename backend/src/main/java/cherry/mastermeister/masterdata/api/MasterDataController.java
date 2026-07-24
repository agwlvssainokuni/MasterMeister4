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

package cherry.mastermeister.masterdata.api;

import cherry.mastermeister.common.exception.InvalidQueryConditionException;
import cherry.mastermeister.masterdata.MasterDataService;
import cherry.mastermeister.masterdata.dto.AccessibleConnectionResponse;
import cherry.mastermeister.masterdata.dto.AccessibleTableResponse;
import cherry.mastermeister.masterdata.dto.BatchOperationItemRequest;
import cherry.mastermeister.masterdata.dto.BatchOperationRequest;
import cherry.mastermeister.masterdata.dto.BatchOperationResultResponse;
import cherry.mastermeister.masterdata.dto.RecordFilterRequest;
import cherry.mastermeister.masterdata.dto.RecordPageResponse;
import cherry.mastermeister.masterdata.model.RecordFilterCondition;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * frontend-components.md §1〜3。logical-components.md §1。
 * 一般ユーザ向け新規APIサーフェス（{@code /api/master-data/**}）。既存の
 * {@code /api/admin/**}とは独立し、SecurityConfigの既存ルール（{@code /api/**}は
 * ロール不問で認証済みなら許可）がそのまま適用される（新規ルール追加は不要、
 * nfr-design-patterns.md §3.2からの簡略化。詳細はapi-layer-summary.md参照）。
 */
@RestController
@RequestMapping("/api/master-data/connections")
public class MasterDataController {

    private static final int DEFAULT_PAGE_SIZE = 50;

    private final MasterDataService masterDataService;
    private final ObjectMapper objectMapper;

    public MasterDataController(MasterDataService masterDataService, ObjectMapper objectMapper) {
        this.masterDataService = masterDataService;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public ResponseEntity<List<AccessibleConnectionResponse>> listConnections(
            @AuthenticationPrincipal Jwt principal) {
        List<AccessibleConnectionResponse> responses = masterDataService
                .listAccessibleConnections(currentUserId(principal)).stream()
                .map(AccessibleConnectionResponse::from)
                .toList();
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/{connectionId}/tables")
    public ResponseEntity<List<AccessibleTableResponse>> listTables(@PathVariable Long connectionId,
                                                                      @AuthenticationPrincipal Jwt principal) {
        List<AccessibleTableResponse> responses = masterDataService
                .listAccessibleTables(currentUserId(principal), connectionId).stream()
                .map(AccessibleTableResponse::from)
                .toList();
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/{connectionId}/tables/{schemaName}/{tableName}/records")
    public ResponseEntity<RecordPageResponse> listRecords(@PathVariable Long connectionId,
                                                           @PathVariable String schemaName,
                                                           @PathVariable String tableName,
                                                           @RequestParam(required = false) String filter,
                                                           @RequestParam(required = false) String where,
                                                           @RequestParam(required = false) String orderBy,
                                                           @RequestParam(defaultValue = "0") int page,
                                                           @RequestParam(defaultValue = "" + DEFAULT_PAGE_SIZE)
                                                           int pageSize,
                                                           @AuthenticationPrincipal Jwt principal) {
        List<RecordFilterCondition> filters = parseFilters(filter);
        var recordPage = masterDataService.getRecords(currentUserId(principal), connectionId, schemaName, tableName,
                filters, where, orderBy, page, pageSize);
        return ResponseEntity.ok(RecordPageResponse.from(recordPage));
    }

    @PostMapping("/{connectionId}/tables/{schemaName}/{tableName}/records/batch")
    public ResponseEntity<BatchOperationResultResponse> applyBatch(@PathVariable Long connectionId,
                                                                    @PathVariable String schemaName,
                                                                    @PathVariable String tableName,
                                                                    @Valid @RequestBody BatchOperationRequest request,
                                                                    @AuthenticationPrincipal Jwt principal) {
        var result = masterDataService.applyBatch(currentUserId(principal), connectionId, schemaName, tableName,
                request.operations().stream().map(BatchOperationItemRequest::toModel).toList());
        return ResponseEntity.ok(BatchOperationResultResponse.from(result));
    }

    /**
     * {@code filter}クエリパラメータはJSON配列としてエンコードされる（frontend-components.md §3の
     * 実装判断）。パース失敗はSQL手入力同様の入力検証エラーとして扱う。
     */
    private List<RecordFilterCondition> parseFilters(String filter) {
        if (filter == null || filter.isBlank()) {
            return List.of();
        }
        try {
            List<RecordFilterRequest> requests = objectMapper.readValue(filter,
                    new TypeReference<List<RecordFilterRequest>>() {
                    });
            return requests.stream().map(RecordFilterRequest::toModel).toList();
        } catch (Exception e) {
            throw new InvalidQueryConditionException();
        }
    }

    private Long currentUserId(Jwt principal) {
        return Long.valueOf(principal.getSubject());
    }
}
