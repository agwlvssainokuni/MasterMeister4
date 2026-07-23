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

package cherry.mastermeister.group.api;

import cherry.mastermeister.group.GroupService;
import cherry.mastermeister.group.dto.GroupMemberRequest;
import cherry.mastermeister.group.dto.GroupMemberResponse;
import cherry.mastermeister.group.dto.GroupRequest;
import cherry.mastermeister.group.dto.GroupResponse;
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

import java.util.List;

/**
 * frontend-components.md §1。全エンドポイントとも、既存のSecurityFilterChain設定
 * （{@code /api/admin/**}）により管理者ロール必須（追加設定不要）。
 */
@RestController
@RequestMapping("/api/admin/groups")
public class GroupController {

    private final GroupService groupService;

    public GroupController(GroupService groupService) {
        this.groupService = groupService;
    }

    @GetMapping
    public ResponseEntity<List<GroupResponse>> list() {
        List<GroupResponse> responses = groupService.listGroups().stream().map(GroupResponse::from).toList();
        return ResponseEntity.ok(responses);
    }

    @PostMapping
    public ResponseEntity<GroupResponse> create(@Valid @RequestBody GroupRequest request,
                                                 @AuthenticationPrincipal Jwt principal) {
        var group = groupService.createGroup(request.name(), currentUserId(principal));
        return ResponseEntity.ok(GroupResponse.from(group));
    }

    @PutMapping("/{id}")
    public ResponseEntity<GroupResponse> rename(@PathVariable Long id, @Valid @RequestBody GroupRequest request,
                                                 @AuthenticationPrincipal Jwt principal) {
        var group = groupService.renameGroup(id, request.name(), currentUserId(principal));
        return ResponseEntity.ok(GroupResponse.from(group));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, @AuthenticationPrincipal Jwt principal) {
        groupService.deleteGroup(id, currentUserId(principal));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/members")
    public ResponseEntity<List<GroupMemberResponse>> listMembers(@PathVariable Long id) {
        List<GroupMemberResponse> responses = groupService.listMembers(id).stream()
                .map(GroupMemberResponse::from)
                .toList();
        return ResponseEntity.ok(responses);
    }

    @PostMapping("/{id}/members")
    public ResponseEntity<Void> addMember(@PathVariable Long id, @Valid @RequestBody GroupMemberRequest request,
                                           @AuthenticationPrincipal Jwt principal) {
        groupService.addUserToGroup(id, request.userId(), currentUserId(principal));
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/members/{userId}")
    public ResponseEntity<Void> removeMember(@PathVariable Long id, @PathVariable Long userId,
                                              @AuthenticationPrincipal Jwt principal) {
        groupService.removeUserFromGroup(id, userId, currentUserId(principal));
        return ResponseEntity.noContent().build();
    }

    private Long currentUserId(Jwt principal) {
        return Long.valueOf(principal.getSubject());
    }
}
