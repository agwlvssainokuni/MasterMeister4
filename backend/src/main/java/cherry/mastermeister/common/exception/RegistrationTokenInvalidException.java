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
 * BR-REG-02。登録トークンが存在しない・使用済み・期限切れのいずれか。
 */
public class RegistrationTokenInvalidException extends ApiException {

    public static RegistrationTokenInvalidException expired() {
        return new RegistrationTokenInvalidException("REGISTRATION_TOKEN_EXPIRED");
    }

    public static RegistrationTokenInvalidException invalid() {
        return new RegistrationTokenInvalidException("REGISTRATION_TOKEN_INVALID");
    }

    private RegistrationTokenInvalidException(String code) {
        super(code, HttpStatus.BAD_REQUEST);
    }
}
