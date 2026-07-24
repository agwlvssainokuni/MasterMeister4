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
 * BR-MASTER-01。指定されたテーブル/ビューが存在しない場合、または実効主権限がNONEで
 * アクセス不可の場合。存在有無と権限有無を区別せず同一の404として扱う
 * （テーブルの存在自体を非公開情報として扱うフェイルクローズな設計判断）。
 */
public class MasterDataTableNotAccessibleException extends ApiException {

    public MasterDataTableNotAccessibleException() {
        super("MASTER_DATA_TABLE_NOT_ACCESSIBLE", HttpStatus.NOT_FOUND);
    }
}
