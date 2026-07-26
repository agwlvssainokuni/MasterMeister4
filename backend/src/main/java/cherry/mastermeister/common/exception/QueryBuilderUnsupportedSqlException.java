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
 * BR-QUERYBUILDER-07。リバースエンジニアリング対象のSQLが、タブUIで表現できない構文要素
 * （サブクエリ・UNION・CASE式・ウィンドウ関数、非対応のJOIN条件・WHERE/HAVING構造・JOIN種別・
 * 集計関数）を含む場合。参照テーブル/カラムのアクセス可否とは無関係の、構文的な非対応を示す
 * （アクセス権限不足は{@link QueryBuilderReferenceNotAccessibleException}で区別する）。
 */
public class QueryBuilderUnsupportedSqlException extends ApiException {

    public QueryBuilderUnsupportedSqlException() {
        super("QUERY_BUILDER_UNSUPPORTED_SQL", HttpStatus.UNPROCESSABLE_ENTITY);
    }
}
