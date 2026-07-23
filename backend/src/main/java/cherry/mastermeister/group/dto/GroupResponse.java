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

package cherry.mastermeister.group.dto;

import cherry.mastermeister.group.entity.Group;

import java.time.Instant;

/**
 * frontend-components.md §1。一覧表示に所属ユーザ数を含める。
 */
public record GroupResponse(Long id, String name, int memberCount, Instant createdAt) {

    public static GroupResponse from(Group group) {
        return new GroupResponse(group.getId(), group.getName(), group.getMemberships().size(),
                group.getCreatedAt());
    }
}
