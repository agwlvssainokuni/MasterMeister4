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
 * BR-MASTER-04。SQL手入力のWHERE/ORDER BY句が許可された構文要素以外を含む場合、
 * または存在しないカラムを参照する場合。{@link ApiException}のサブクラスであるため、
 * 既存の{@code GlobalExceptionHandler}の汎用ハンドラでVALIDATION_ERROR相当（400）に変換される
 * （個別の{@code @ExceptionHandler}追加は不要）。
 */
public class InvalidQueryConditionException extends ApiException {

    public InvalidQueryConditionException() {
        super("INVALID_QUERY_CONDITION", HttpStatus.BAD_REQUEST);
    }
}
