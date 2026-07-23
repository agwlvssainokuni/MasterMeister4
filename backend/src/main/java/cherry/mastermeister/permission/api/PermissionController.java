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

package cherry.mastermeister.permission.api;

import cherry.mastermeister.permission.PermissionService;
import cherry.mastermeister.permission.PermissionYamlService;
import cherry.mastermeister.permission.dto.PermissionEntryRequest;
import cherry.mastermeister.permission.dto.PermissionEntryResponse;
import cherry.mastermeister.permission.dto.PermissionImportRequest;
import cherry.mastermeister.permission.entity.PrincipalType;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
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
 * frontend-components.md §2。全エンドポイントとも、既存のSecurityFilterChain設定
 * （{@code /api/admin/**}）により管理者ロール必須（追加設定不要）。
 * {@code DELETE}はリクエストボディを持たない設計のため、対象キーをクエリパラメータで指定する
 * （レビュー指摘の反映、frontend-components.md §2.1）。
 */
@RestController
@RequestMapping("/api/admin/permissions/{connectionId}")
public class PermissionController {

    private final PermissionService permissionService;
    private final PermissionYamlService permissionYamlService;

    public PermissionController(PermissionService permissionService, PermissionYamlService permissionYamlService) {
        this.permissionService = permissionService;
        this.permissionYamlService = permissionYamlService;
    }

    @GetMapping
    public ResponseEntity<List<PermissionEntryResponse>> list(@PathVariable Long connectionId,
                                                               @RequestParam PrincipalType principalType,
                                                               @RequestParam Long principalId) {
        List<PermissionEntryResponse> responses = permissionService
                .listPermissions(connectionId, principalType, principalId).stream()
                .map(PermissionEntryResponse::from)
                .toList();
        return ResponseEntity.ok(responses);
    }

    @PutMapping
    public ResponseEntity<PermissionEntryResponse> set(@PathVariable Long connectionId,
                                                        @Valid @RequestBody PermissionEntryRequest request,
                                                        @AuthenticationPrincipal Jwt principal) {
        var permission = permissionService.setPermission(connectionId, request.principalType(),
                request.principalId(), request.schemaName(), request.tableName(), request.columnName(),
                request.primaryPermission(), request.createPermission(), request.deletePermission(),
                currentUserId(principal));
        return ResponseEntity.ok(PermissionEntryResponse.from(permission));
    }

    @DeleteMapping
    public ResponseEntity<Void> unset(@PathVariable Long connectionId, @RequestParam PrincipalType principalType,
                                       @RequestParam Long principalId, @RequestParam String schemaName,
                                       @RequestParam(required = false) String tableName,
                                       @RequestParam(required = false) String columnName,
                                       @AuthenticationPrincipal Jwt principal) {
        permissionService.unsetPermission(connectionId, principalType, principalId, schemaName, tableName,
                columnName, currentUserId(principal));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/export")
    public ResponseEntity<String> export(@PathVariable Long connectionId, @AuthenticationPrincipal Jwt principal) {
        String yaml = permissionYamlService.exportToYaml(connectionId, currentUserId(principal));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/x-yaml"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("permissions-" + connectionId + ".yaml").build()
                                .toString())
                .body(yaml);
    }

    @PostMapping("/import")
    public ResponseEntity<Void> importYaml(@PathVariable Long connectionId,
                                            @Valid @RequestBody PermissionImportRequest request,
                                            @AuthenticationPrincipal Jwt principal) {
        permissionYamlService.importFromYaml(connectionId, request.yaml(), currentUserId(principal));
        return ResponseEntity.noContent().build();
    }

    private Long currentUserId(Jwt principal) {
        return Long.valueOf(principal.getSubject());
    }
}
