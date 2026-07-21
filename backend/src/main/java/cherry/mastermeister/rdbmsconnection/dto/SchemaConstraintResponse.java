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

package cherry.mastermeister.rdbmsconnection.dto;

import cherry.mastermeister.rdbmsconnection.entity.ConstraintType;
import cherry.mastermeister.rdbmsconnection.entity.SchemaConstraint;

import java.util.List;

public record SchemaConstraintResponse(
        ConstraintType constraintType,
        String constraintName,
        List<String> columnNames,
        String referencedTable,
        List<String> referencedColumns
) {

    public static SchemaConstraintResponse from(SchemaConstraint constraint) {
        return new SchemaConstraintResponse(constraint.getConstraintType(), constraint.getConstraintName(),
                constraint.getColumnNames(), constraint.getReferencedTable(), constraint.getReferencedColumns());
    }
}
