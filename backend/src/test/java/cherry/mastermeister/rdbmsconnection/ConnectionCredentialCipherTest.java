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

package cherry.mastermeister.rdbmsconnection;

import cherry.mastermeister.common.config.AppProperties;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConnectionCredentialCipherTest {

    private static final String KEY_1 = "MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE=";
    private static final String KEY_2 = "YWJjZGVmZ2hpamtsbW5vcHFyc3R1dnd4eXphYmNkZWY=";

    private AppProperties appProperties(String encryptionKeys) {
        return new AppProperties(
                new AppProperties.Jwt("0123456789012345678901234567890123456789", Duration.ofMinutes(10),
                        Duration.ofDays(1)),
                new AppProperties.Password(10, 8),
                new AppProperties.LoginAttempt(5, Duration.ofMinutes(15)),
                new AppProperties.UserRegistration(Duration.ofHours(3), 3, Duration.ofHours(1)),
                new AppProperties.AdminBootstrap("", ""),
                new AppProperties.Frontend("https://example.com"),
                new AppProperties.Datasource("./data/test"),
                new AppProperties.Mail("no-reply@example.com"),
                new AppProperties.Rdbms(encryptionKeys));
    }

    @Test
    void encryptThenDecrypt_roundTripsWithCurrentKey() {
        ConnectionCredentialCipher cipher = new ConnectionCredentialCipher(appProperties("1:" + KEY_1));

        ConnectionCredentialCipher.EncryptedCredential encrypted = cipher.encrypt("s3cr3t");

        assertThat(encrypted.keyId()).isEqualTo(1);
        assertThat(cipher.decrypt(encrypted.encryptedValue(), encrypted.keyId())).isEqualTo("s3cr3t");
    }

    @Test
    void encrypt_usesHighestKeyIdAsCurrentKey_perKeyRotation() {
        ConnectionCredentialCipher cipher = new ConnectionCredentialCipher(appProperties("1:" + KEY_1 + ",2:" + KEY_2));

        ConnectionCredentialCipher.EncryptedCredential encrypted = cipher.encrypt("s3cr3t");

        assertThat(encrypted.keyId()).isEqualTo(2);
    }

    @Test
    void decrypt_stillWorksWithOlderKeyId_afterRotation() {
        ConnectionCredentialCipher beforeRotation = new ConnectionCredentialCipher(appProperties("1:" + KEY_1));
        ConnectionCredentialCipher.EncryptedCredential encryptedWithKey1 = beforeRotation.encrypt("s3cr3t");

        ConnectionCredentialCipher afterRotation = new ConnectionCredentialCipher(
                appProperties("1:" + KEY_1 + ",2:" + KEY_2));

        assertThat(afterRotation.decrypt(encryptedWithKey1.encryptedValue(), 1)).isEqualTo("s3cr3t");
    }

    @Test
    void decrypt_throws_whenKeyIdNotConfigured() {
        ConnectionCredentialCipher cipher = new ConnectionCredentialCipher(appProperties("1:" + KEY_1));
        ConnectionCredentialCipher.EncryptedCredential encrypted = cipher.encrypt("s3cr3t");

        assertThatThrownBy(() -> cipher.decrypt(encrypted.encryptedValue(), 99))
                .isInstanceOf(IllegalStateException.class);
    }
}
