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
 * business-rules.md BR-API-01。GlobalExceptionHandlerが{@code { code, message }}形式へ変換する
 * 業務例外の基底クラス。ユーザ向け{@code message}はNFR-7.3（多言語対応）に基づき、GlobalExceptionHandlerが
 * {@code MessageSource}経由でリクエストの言語設定に応じて解決する（メッセージキー: {@code error.<code>}）。
 * このクラス自体は開発者向けのログ出力用にコード自体を例外メッセージとして保持する。
 */
public class ApiException extends RuntimeException {

    private final String code;
    private final HttpStatus status;
    private final Object[] messageArgs;

    public ApiException(String code, HttpStatus status) {
        this(code, status, new Object[0]);
    }

    public ApiException(String code, HttpStatus status, Object... messageArgs) {
        super(code);
        this.code = code;
        this.status = status;
        this.messageArgs = messageArgs;
    }

    public String getCode() {
        return code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public Object[] getMessageArgs() {
        return messageArgs;
    }
}
