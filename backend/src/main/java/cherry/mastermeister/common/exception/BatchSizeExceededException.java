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
 * NFR-05-05。一括反映バッチの操作件数が{@code mm.app.masterdata.batch-max-size}を超える場合。
 * DBアクセス前に拒否する。
 */
public class BatchSizeExceededException extends ApiException {

    public BatchSizeExceededException(int maxSize) {
        super("BATCH_SIZE_EXCEEDED", HttpStatus.BAD_REQUEST, maxSize);
    }
}
