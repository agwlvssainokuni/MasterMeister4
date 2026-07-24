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

package cherry.mastermeister.masterdata.model;

import java.util.List;
import java.util.Map;

/**
 * domain-entities.md §6。BR-MASTER-10。レコード一覧取得の結果。
 * {@code creatable}/{@code deletable}は{@code AccessibleTable}と同値だが、レコード一覧画面が
 * テーブル一覧を経由せず直接遷移・再読込された場合でも判定できるようレスポンスに含める。
 */
public record RecordPage(
        List<RecordColumn> columns,
        List<Map<String, String>> rows,
        int page,
        int pageSize,
        long totalCount,
        boolean creatable,
        boolean deletable
) {
}
