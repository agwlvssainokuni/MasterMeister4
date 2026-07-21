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
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * nfr-design/logical-components.md §1。接続パスワードのAES-256-GCM暗号化・復号
 * （SECURITY-01, SECURITY-12）。鍵は世代（keyId）管理し、現在鍵（最大のkeyId）で
 * 新規暗号化、全世代の鍵で復号を試行可能とする（tech-stack-decisions.md §1、鍵ローテーション）。
 */
@Component
public class ConnectionCredentialCipher {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH_BYTES = 12;
    private static final int GCM_TAG_LENGTH_BITS = 128;

    private final Map<Integer, SecretKeySpec> keysByKeyId;
    private final int currentKeyId;
    private final SecureRandom secureRandom = new SecureRandom();

    public ConnectionCredentialCipher(AppProperties appProperties) {
        var keys = appProperties.rdbms().parsedEncryptionKeys();
        this.keysByKeyId = keys.stream()
                .collect(Collectors.toMap(AppProperties.Rdbms.EncryptionKey::keyId,
                        k -> new SecretKeySpec(k.key(), "AES")));
        this.currentKeyId = keys.stream().mapToInt(AppProperties.Rdbms.EncryptionKey::keyId).max().orElseThrow();
    }

    public EncryptedCredential encrypt(String plainPassword) {
        SecretKeySpec key = keysByKeyId.get(currentKeyId);
        byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
        secureRandom.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] cipherText = cipher.doFinal(plainPassword.getBytes(StandardCharsets.UTF_8));
            byte[] combined = ByteBuffer.allocate(iv.length + cipherText.length).put(iv).put(cipherText).array();
            return new EncryptedCredential(Base64.getEncoder().encodeToString(combined), currentKeyId);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to encrypt connection password", e);
        }
    }

    /**
     * @throws IllegalStateException 指定されたkeyIdが設定済みの鍵一覧に存在しない場合
     *                                （運用上、古い鍵を環境変数から削除してしまった場合等）
     */
    public String decrypt(String encryptedValue, int keyId) {
        SecretKeySpec key = keysByKeyId.get(keyId);
        if (key == null) {
            throw new IllegalStateException("No encryption key configured for keyId=" + keyId);
        }
        byte[] combined = Base64.getDecoder().decode(encryptedValue);
        byte[] iv = Arrays.copyOfRange(combined, 0, GCM_IV_LENGTH_BYTES);
        byte[] cipherText = Arrays.copyOfRange(combined, GCM_IV_LENGTH_BYTES, combined.length);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to decrypt connection password for keyId=" + keyId, e);
        }
    }

    public record EncryptedCredential(String encryptedValue, int keyId) {
    }
}
