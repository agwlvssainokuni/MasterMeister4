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
 * BR-QUERYBUILDER-11。SELECT句に集計関数を含む場合、GROUP BYに含まれない非集計列が
 * SELECT句に存在してはならないという標準SQLのGROUP BY整合性制約に違反する場合。
 */
public class QueryBuilderInvalidGroupByException extends ApiException {

    public QueryBuilderInvalidGroupByException() {
        super("QUERY_BUILDER_INVALID_GROUP_BY", HttpStatus.BAD_REQUEST);
    }
}
