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
 * BR-RDBMS-07。スキーマ取込の一部テーブル読取失敗・タイムアウト等、オールオアナッシングで
 * 取込処理全体を失敗として扱う場合。
 */
public class SchemaImportFailedException extends ApiException {

    public SchemaImportFailedException() {
        super("SCHEMA_IMPORT_FAILED", HttpStatus.BAD_GATEWAY);
    }
}
