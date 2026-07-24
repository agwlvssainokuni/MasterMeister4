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

import cherry.mastermeister.rdbmsconnection.entity.TableType;

/**
 * domain-entities.md §2。BR-MASTER-01〜03。アクセス可能なテーブル/ビュー一覧の1件。
 */
public record AccessibleTable(
        String schemaName,
        String tableName,
        TableType tableType,
        boolean creatable,
        boolean deletable
) {
}
