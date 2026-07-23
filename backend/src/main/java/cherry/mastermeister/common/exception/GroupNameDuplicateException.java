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

package cherry.mastermeister.common.exception;

import org.springframework.http.HttpStatus;

/**
 * グループ作成・改名時、指定された名前が既存の別グループと重複する場合。
 */
public class GroupNameDuplicateException extends ApiException {

    public GroupNameDuplicateException() {
        super("GROUP_NAME_DUPLICATE", HttpStatus.CONFLICT);
    }
}
