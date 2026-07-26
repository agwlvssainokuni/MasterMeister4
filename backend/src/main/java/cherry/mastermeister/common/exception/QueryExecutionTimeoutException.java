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
 * NFR Requirements Q3=A。クエリ実行が{@code mm.app.query.execution-timeout-seconds}を
 * 超過した場合。{@code org.springframework.dao.QueryTimeoutException}をcatchして変換する
 * （nfr-design-patterns.md §1.4）。
 */
public class QueryExecutionTimeoutException extends ApiException {

    public QueryExecutionTimeoutException() {
        super("QUERY_EXECUTION_TIMEOUT", HttpStatus.REQUEST_TIMEOUT);
    }
}
