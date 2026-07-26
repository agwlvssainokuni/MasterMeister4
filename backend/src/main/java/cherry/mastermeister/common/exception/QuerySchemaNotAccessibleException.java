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
 * BR-QUERY-02。実行対象スキーマが許可リスト（実行者が接続内のいずれかのテーブル/カラムに
 * 実効主権限READ以上を持つスキーマ）に含まれない場合。スキーマ一覧自体が既にアクセス可能な
 * 範囲のみを返却する設計のため、UNIT-05のMasterDataTableNotAccessibleExceptionとは異なり
 * 存在を隠す必要はなく403とする（nfr-design-patterns.md §1.1）。
 */
public class QuerySchemaNotAccessibleException extends ApiException {

    public QuerySchemaNotAccessibleException() {
        super("QUERY_SCHEMA_NOT_ACCESSIBLE", HttpStatus.FORBIDDEN);
    }
}
