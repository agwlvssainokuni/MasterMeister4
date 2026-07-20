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
 * BR-PWD-01（最小文字数）・BR-PWD-02（既知漏洩パスワード）。
 */
public class PasswordPolicyViolationException extends ApiException {

    public static PasswordPolicyViolationException tooShort(int minLength) {
        return new PasswordPolicyViolationException("PASSWORD_TOO_SHORT", minLength);
    }

    public static PasswordPolicyViolationException compromised() {
        return new PasswordPolicyViolationException("PASSWORD_COMPROMISED");
    }

    private PasswordPolicyViolationException(String code, Object... messageArgs) {
        super(code, HttpStatus.BAD_REQUEST, messageArgs);
    }
}
