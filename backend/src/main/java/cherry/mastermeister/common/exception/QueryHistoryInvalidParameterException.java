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
 * nfr-design-patterns.md §1.1。絞込パラメータ（ページサイズ超過、実行日時範囲の開始&gt;終了）の
 * 検証に失敗した場合。実装時の判断: 個々の{@code @RequestParam}で受け取るため（Step 1.1の
 * 実装判断参照）Bean Validationの{@code @Valid}が効かず、Controller内で明示的に検証する。
 */
public class QueryHistoryInvalidParameterException extends ApiException {

    public QueryHistoryInvalidParameterException() {
        super("QUERY_HISTORY_INVALID_PARAMETER", HttpStatus.BAD_REQUEST);
    }
}
