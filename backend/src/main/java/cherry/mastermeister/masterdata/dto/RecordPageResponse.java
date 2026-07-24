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

package cherry.mastermeister.masterdata.dto;

import cherry.mastermeister.masterdata.model.RecordPage;

import java.util.List;
import java.util.Map;

public record RecordPageResponse(
        List<RecordColumnResponse> columns,
        List<Map<String, String>> rows,
        int page,
        int pageSize,
        long totalCount,
        boolean creatable,
        boolean deletable
) {

    public static RecordPageResponse from(RecordPage page) {
        return new RecordPageResponse(page.columns().stream().map(RecordColumnResponse::from).toList(),
                page.rows(), page.page(), page.pageSize(), page.totalCount(), page.creatable(), page.deletable());
    }
}
