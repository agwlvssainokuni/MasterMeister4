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

package cherry.mastermeister.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;

@ConfigurationProperties(prefix = "mm.app")
public record AppProperties(
        Jwt jwt,
        Password password,
        LoginAttempt loginAttempt,
        UserRegistration userRegistration,
        AdminBootstrap adminBootstrap,
        Frontend frontend,
        Datasource datasource
) {

    public AppProperties {
        Objects.requireNonNull(jwt, "mm.app.jwt must be configured");
        Objects.requireNonNull(password, "mm.app.password must be configured");
        Objects.requireNonNull(loginAttempt, "mm.app.login-attempt must be configured");
        Objects.requireNonNull(userRegistration, "mm.app.user-registration must be configured");
        Objects.requireNonNull(adminBootstrap, "mm.app.admin-bootstrap must be configured");
        Objects.requireNonNull(frontend, "mm.app.frontend must be configured");
        Objects.requireNonNull(datasource, "mm.app.datasource must be configured");
    }

    public record Jwt(String secret, Duration accessTokenExpiry, Duration refreshTokenExpiry) {

        private static final int MIN_SECRET_BYTES = 32;

        public Jwt {
            if (secret == null || secret.isBlank()) {
                throw new IllegalArgumentException("mm.app.jwt.secret must not be blank");
            }
            if (secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
                throw new IllegalArgumentException(
                        "mm.app.jwt.secret must be at least " + MIN_SECRET_BYTES + " bytes (256 bits) for HS256");
            }
            Objects.requireNonNull(accessTokenExpiry, "mm.app.jwt.access-token-expiry must be configured");
            Objects.requireNonNull(refreshTokenExpiry, "mm.app.jwt.refresh-token-expiry must be configured");
        }
    }

    public record Password(int bcryptStrength, int minLength) {

        public Password {
            if (bcryptStrength < 4 || bcryptStrength > 31) {
                throw new IllegalArgumentException("mm.app.password.bcrypt-strength must be between 4 and 31");
            }
            if (minLength < 1) {
                throw new IllegalArgumentException("mm.app.password.min-length must be positive");
            }
        }
    }

    public record LoginAttempt(int maxFailures, Duration lockDuration) {

        public LoginAttempt {
            if (maxFailures < 1) {
                throw new IllegalArgumentException("mm.app.login-attempt.max-failures must be positive");
            }
            Objects.requireNonNull(lockDuration, "mm.app.login-attempt.lock-duration must be configured");
        }
    }

    public record UserRegistration(Duration tokenExpiry, int rateLimitMaxRequests, Duration rateLimitWindow) {

        public UserRegistration {
            Objects.requireNonNull(tokenExpiry, "mm.app.user-registration.token-expiry must be configured");
            if (rateLimitMaxRequests < 1) {
                throw new IllegalArgumentException(
                        "mm.app.user-registration.rate-limit-max-requests must be positive");
            }
            Objects.requireNonNull(rateLimitWindow, "mm.app.user-registration.rate-limit-window must be configured");
        }
    }

    /**
     * email/passwordはブートストラップ対象外運用（既存管理者がいる等）を許容するため、
     * 空文字列・nullを許容する（AdminBootstrapService側で設定有無を判定する）。
     */
    public record AdminBootstrap(String email, String password) {
    }

    public record Frontend(String baseUrl) {

        public Frontend {
            if (baseUrl == null || baseUrl.isBlank()) {
                throw new IllegalArgumentException("mm.app.frontend.base-url must not be blank");
            }
        }
    }

    public record Datasource(String path) {

        public Datasource {
            if (path == null || path.isBlank()) {
                throw new IllegalArgumentException("mm.app.datasource.path must not be blank");
            }
        }
    }
}
