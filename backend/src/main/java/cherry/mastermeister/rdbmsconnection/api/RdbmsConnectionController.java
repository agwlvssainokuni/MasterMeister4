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

package cherry.mastermeister.rdbmsconnection.api;

import cherry.mastermeister.common.exception.SchemaNotImportedException;
import cherry.mastermeister.rdbmsconnection.RdbmsConnectionService;
import cherry.mastermeister.rdbmsconnection.SchemaIntrospectionService;
import cherry.mastermeister.rdbmsconnection.dto.ConnectionTestRequest;
import cherry.mastermeister.rdbmsconnection.dto.ConnectionTestResult;
import cherry.mastermeister.rdbmsconnection.dto.RdbmsConnectionRequest;
import cherry.mastermeister.rdbmsconnection.dto.RdbmsConnectionResponse;
import cherry.mastermeister.rdbmsconnection.dto.SchemaSnapshotResponse;
import cherry.mastermeister.rdbmsconnection.entity.RdbmsConnection;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/**
 * frontend-components.md §1〜2。全エンドポイントとも、既存のSecurityFilterChain設定
 * （{@code /api/admin/**}）により管理者ロール必須（nfr-design-patterns.md §3.2、追加設定不要）。
 */
@RestController
@RequestMapping("/api/admin/rdbms-connections")
public class RdbmsConnectionController {

    private final RdbmsConnectionService rdbmsConnectionService;
    private final SchemaIntrospectionService schemaIntrospectionService;

    public RdbmsConnectionController(RdbmsConnectionService rdbmsConnectionService,
                                      SchemaIntrospectionService schemaIntrospectionService) {
        this.rdbmsConnectionService = rdbmsConnectionService;
        this.schemaIntrospectionService = schemaIntrospectionService;
    }

    @GetMapping
    public ResponseEntity<List<RdbmsConnectionResponse>> list() {
        List<RdbmsConnectionResponse> responses = rdbmsConnectionService.listConnections().stream()
                .map(this::toResponse)
                .toList();
        return ResponseEntity.ok(responses);
    }

    @PostMapping
    public ResponseEntity<RdbmsConnectionResponse> register(@Valid @RequestBody RdbmsConnectionRequest request,
                                                              @AuthenticationPrincipal Jwt principal) {
        RdbmsConnection connection = rdbmsConnectionService.registerConnection(request.displayName(),
                request.dbType(), request.host(), request.port(), request.databaseName(),
                request.username(), request.password(), request.additionalParams(), currentUserId(principal));
        return ResponseEntity.ok(toResponse(connection));
    }

    @PutMapping("/{id}")
    public ResponseEntity<RdbmsConnectionResponse> update(@PathVariable Long id,
                                                            @Valid @RequestBody RdbmsConnectionRequest request,
                                                            @AuthenticationPrincipal Jwt principal) {
        RdbmsConnection connection = rdbmsConnectionService.updateConnection(id, request.displayName(),
                request.dbType(), request.host(), request.port(), request.databaseName(),
                request.username(), request.password(), request.additionalParams(), currentUserId(principal));
        return ResponseEntity.ok(toResponse(connection));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, @AuthenticationPrincipal Jwt principal) {
        rdbmsConnectionService.deleteConnection(id, currentUserId(principal));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/test")
    public ResponseEntity<ConnectionTestResult> testSaved(@PathVariable Long id) {
        return ResponseEntity.ok(ConnectionTestResult.from(rdbmsConnectionService.testConnection(id)));
    }

    @PostMapping("/test")
    public ResponseEntity<ConnectionTestResult> testUnsaved(@Valid @RequestBody ConnectionTestRequest request) {
        var outcome = rdbmsConnectionService.testConnectionUnsaved(request.dbType(), request.host(), request.port(),
                request.databaseName(), request.username(), request.password(),
                request.additionalParams());
        return ResponseEntity.ok(ConnectionTestResult.from(outcome));
    }

    @PostMapping("/{id}/schema-refresh")
    public ResponseEntity<SchemaSnapshotResponse> refreshSchema(@PathVariable Long id,
                                                                  @AuthenticationPrincipal Jwt principal) {
        var snapshot = schemaIntrospectionService.refreshSchema(id, currentUserId(principal));
        return ResponseEntity.ok(SchemaSnapshotResponse.from(snapshot));
    }

    @GetMapping("/{id}/schema")
    public ResponseEntity<SchemaSnapshotResponse> getSchema(@PathVariable Long id) {
        var snapshot = schemaIntrospectionService.getSchema(id).orElseThrow(SchemaNotImportedException::new);
        return ResponseEntity.ok(SchemaSnapshotResponse.from(snapshot));
    }

    private RdbmsConnectionResponse toResponse(RdbmsConnection connection) {
        Instant schemaImportedAt = schemaIntrospectionService.getSchema(connection.getId())
                .map(snapshot -> snapshot.getImportedAt())
                .orElse(null);
        return RdbmsConnectionResponse.from(connection, schemaImportedAt);
    }

    private Long currentUserId(Jwt principal) {
        return Long.valueOf(principal.getSubject());
    }
}
