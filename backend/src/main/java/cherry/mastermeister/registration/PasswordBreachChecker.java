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

package cherry.mastermeister.registration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * business-rules.md BR-PWD-02。Have I Been Pwned（HIBP）のk-Anonymity APIを用いた
 * 既知漏洩パスワードチェック。nfr-design-patterns.md §1.1のとおり、タイムアウト3秒・
 * フェイルオープン（呼び出し失敗時はチェックをスキップし処理を継続する）。
 */
@Component
public class PasswordBreachChecker {

    private static final Logger log = LoggerFactory.getLogger(PasswordBreachChecker.class);

    private static final int TIMEOUT_MILLIS = 3000;
    private static final String HIBP_RANGE_BASE_URL = "https://api.pwnedpasswords.com/range/";

    private final RestClient restClient;

    public PasswordBreachChecker(RestClient.Builder restClientBuilder) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(TIMEOUT_MILLIS);
        requestFactory.setReadTimeout(TIMEOUT_MILLIS);
        this.restClient = restClientBuilder
                .baseUrl(HIBP_RANGE_BASE_URL)
                .requestFactory(requestFactory)
                .build();
    }

    /**
     * テスト専用。{@code MockRestServiceServer}等でバインド済みの{@link RestClient}を直接注入する。
     */
    PasswordBreachChecker(RestClient restClient) {
        this.restClient = restClient;
    }

    /**
     * @param rawPassword チェック対象の平文パスワード
     * @return 既知の漏洩パスワードリストに含まれる場合はtrue。API呼び出しに失敗した場合はfalse（フェイルオープン）
     */
    public boolean isBreached(String rawPassword) {
        try {
            String sha1Hex = sha1Hex(rawPassword);
            String prefix = sha1Hex.substring(0, 5);
            String suffix = sha1Hex.substring(5);

            String response = restClient.get()
                    .uri(prefix)
                    .retrieve()
                    .body(String.class);

            if (response == null) {
                return false;
            }
            return response.lines().anyMatch(line -> line.regionMatches(true, 0, suffix, 0, suffix.length())
                    && line.length() > suffix.length() && line.charAt(suffix.length()) == ':');
        } catch (Exception e) {
            log.warn("Password breach check skipped due to an error (fail-open per BR-PWD-02): {}", e.toString());
            return false;
        }
    }

    private String sha1Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().withUpperCase().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-1 must be available", e);
        }
    }
}
