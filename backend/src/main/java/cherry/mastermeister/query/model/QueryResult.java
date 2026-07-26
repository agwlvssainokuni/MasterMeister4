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

package cherry.mastermeister.query.model;

import java.util.List;
import java.util.Map;

/**
 * domain-entities.md §5。クエリ実行結果（永続化しない値オブジェクト）。FR-7.6により
 * ページングは任意のため、page/pageSize/totalCountはページング無効時null。
 */
public record QueryResult(List<String> columns, List<Map<String, String>> rows, Integer page, Integer pageSize,
                           Long totalCount, long rowCount, long durationMillis) {
}
