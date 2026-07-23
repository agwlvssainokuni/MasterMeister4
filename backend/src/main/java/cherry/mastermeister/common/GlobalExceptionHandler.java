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

package cherry.mastermeister.common;

import cherry.mastermeister.common.dto.ApiErrorResponse;
import cherry.mastermeister.common.exception.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Locale;

/**
 * nfr-design-patterns.md §3.5、BR-API-01。例外種別ごとにBR-API-01形式のレスポンスへ変換する。
 * ユーザ向けmessageはNFR-7.3に基づき{@code MessageSource}経由でリクエストの言語設定に応じて解決する
 * （メッセージキー: {@code error.<code>}）。未捕捉の例外は汎用メッセージのみ返し、詳細はログにのみ出力する（SECURITY-09）。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final MessageSource messageSource;

    public GlobalExceptionHandler(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiErrorResponse> handleApiException(ApiException e, Locale locale) {
        return ResponseEntity.status(e.getStatus()).body(toResponse(e.getCode(), e.getMessageArgs(), locale));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationException(MethodArgumentNotValidException e,
                                                                        Locale locale) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(toResponse("VALIDATION_ERROR", null, locale));
    }

    /**
     * UNIT-04追記: 必須のクエリパラメータ欠落・型不一致（例: PermissionControllerの対象キー指定）も
     * BR-API-01のVALIDATION_ERRORとして扱う（従来は未捕捉のままExceptionハンドラに落ち500になっていた）。
     */
    @ExceptionHandler({MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiErrorResponse> handleMissingOrInvalidParameter(Exception e, Locale locale) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(toResponse("VALIDATION_ERROR", null, locale));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpectedException(Exception e, Locale locale) {
        log.error("Unhandled exception", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(toResponse("INTERNAL_SERVER_ERROR", null, locale));
    }

    private ApiErrorResponse toResponse(String code, Object[] args, Locale locale) {
        try {
            String message = messageSource.getMessage("error." + code, args, locale);
            return new ApiErrorResponse(code, message);
        } catch (NoSuchMessageException e) {
            log.warn("No message found for error code: {}", code);
            return new ApiErrorResponse(code, code);
        }
    }
}
