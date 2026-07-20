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
 * BR-REG-03・BR-REG-04。パスワード不一致・ユーザ不存在・未承認/却下/無効化のいずれも、
 * メールアドレス列挙攻撃対策のため同一のコード・メッセージとする。
 */
public class AuthenticationFailedException extends ApiException {

    public AuthenticationFailedException() {
        super("AUTH_INVALID_CREDENTIALS", HttpStatus.UNAUTHORIZED);
    }
}
