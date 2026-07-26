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
 * BR-QUERY-01。SQLが単一のSelect文と認識できない場合（構文エラー・複数ステートメント・
 * 非SELECT文のいずれも含む）。ad-hoc実行・保存クエリの保存時・編集時のいずれでも共通して用いる。
 */
public class NonReadOnlyQueryException extends ApiException {

    public NonReadOnlyQueryException() {
        super("NON_READ_ONLY_QUERY", HttpStatus.BAD_REQUEST);
    }
}
