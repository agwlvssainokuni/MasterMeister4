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

package cherry.mastermeister.permission.entity;

/**
 * domain-entities.md §1。BR-ACCESS-01。序数（ordinal）の昇順が権限の強さの順序と一致する
 * （BR-ACCESS-05のグループ間合成「より許可的な方」の判定に{@link #compareTo(Enum)}を利用する）。
 */
public enum PrimaryPermission {
    NONE,
    READ,
    UPDATE
}
