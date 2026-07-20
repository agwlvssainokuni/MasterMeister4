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

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * BR-PWD-02。HIBP k-Anonymity APIの照合ロジック・フェイルオープンを検証する。
 */
class PasswordBreachCheckerTest {

    private static final String BASE_URL = "https://api.pwnedpasswords.com/range/";

    @Test
    void isBreached_returnsTrue_whenApiReportsMatchingSuffix() {
        String password = "Password1";
        String sha1Hex = sha1Hex(password);
        String prefix = sha1Hex.substring(0, 5);
        String suffix = sha1Hex.substring(5);

        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.baseUrl(BASE_URL).build();
        server.expect(requestTo(BASE_URL + prefix))
                .andRespond(withSuccess(suffix + ":12345\r\nUNRELATEDSUFFIX00000000000000000000:1", MediaType.TEXT_PLAIN));

        PasswordBreachChecker checker = new PasswordBreachChecker(restClient);

        assertThat(checker.isBreached(password)).isTrue();
    }

    @Test
    void isBreached_returnsFalse_whenApiReportsNoMatchingSuffix() {
        String password = "SomeUnbreachedPassword1";
        String sha1Hex = sha1Hex(password);
        String prefix = sha1Hex.substring(0, 5);

        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.baseUrl(BASE_URL).build();
        server.expect(requestTo(BASE_URL + prefix))
                .andRespond(withSuccess("UNRELATEDSUFFIX00000000000000000000:1", MediaType.TEXT_PLAIN));

        PasswordBreachChecker checker = new PasswordBreachChecker(restClient);

        assertThat(checker.isBreached(password)).isFalse();
    }

    @Test
    void isBreached_returnsFalse_whenApiCallFails_failOpenPerBrPwd02() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.baseUrl(BASE_URL).build();
        server.expect(requestTo(BASE_URL + sha1Hex("AnyPassword1").substring(0, 5)))
                .andRespond(withServerError());

        PasswordBreachChecker checker = new PasswordBreachChecker(restClient);

        assertThat(checker.isBreached("AnyPassword1")).isFalse();
    }

    private String sha1Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().withUpperCase().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
