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

package cherry.mastermeister.querybuilder.dto;

/**
 * domain-entities.md §2。BR-QUERYBUILDER-02。対象RDBMS4種（MySQL/MariaDB/PostgreSQL/H2）で
 * 共通してサポートされる範囲に限定し、FULL JOINは含めない。
 */
public enum JoinType {
    INNER,
    LEFT,
    RIGHT
}
