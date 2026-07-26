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
 * nfr-design-patterns.md §2.1。WHERE/HAVING条件の比較値が、列のデータ型分類
 * （{@code ColumnDataTypeCategory}）と矛盾し、型安全なSQLリテラルへ変換できない場合
 * （例: NUMERIC列に対し数値として解釈できない文字列が指定された）。
 */
public class QueryBuilderInvalidLiteralException extends ApiException {

    public QueryBuilderInvalidLiteralException() {
        super("QUERY_BUILDER_INVALID_LITERAL", HttpStatus.BAD_REQUEST);
    }
}
