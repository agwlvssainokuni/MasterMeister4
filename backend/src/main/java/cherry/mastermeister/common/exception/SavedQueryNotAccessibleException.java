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
 * BR-QUERY-09。保存クエリが存在しない、対象接続に属さない、非表示化済みで作成者以外が
 * アクセスした、またはPrivateで作成者以外がアクセスした場合。UNIT-05の
 * MasterDataTableNotAccessibleExceptionと同じフェイルクローズ方針（存在有無とアクセス権限
 * 有無を区別しない）を踏襲し404とする。
 */
public class SavedQueryNotAccessibleException extends ApiException {

    public SavedQueryNotAccessibleException() {
        super("SAVED_QUERY_NOT_ACCESSIBLE", HttpStatus.NOT_FOUND);
    }
}
