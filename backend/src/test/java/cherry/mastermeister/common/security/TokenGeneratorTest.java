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

package cherry.mastermeister.common.security;

import org.junit.jupiter.api.Test;

import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class TokenGeneratorTest {

    private final TokenGenerator tokenGenerator = new TokenGenerator();

    @Test
    void generate_producesUniqueTokens() {
        long distinctCount = IntStream.range(0, 100)
                .mapToObj(i -> tokenGenerator.generate())
                .distinct()
                .count();

        assertThat(distinctCount).isEqualTo(100);
    }

    @Test
    void hash_isDeterministicForSameInput() {
        String token = tokenGenerator.generate();

        assertThat(tokenGenerator.hash(token)).isEqualTo(tokenGenerator.hash(token));
    }

    @Test
    void hash_neverEqualsRawToken() {
        String token = tokenGenerator.generate();

        assertThat(tokenGenerator.hash(token)).isNotEqualTo(token);
    }

    @Test
    void hash_producesDifferentHashesForDifferentInputs() {
        Stream<String> hashes = Stream.of(tokenGenerator.generate(), tokenGenerator.generate())
                .map(tokenGenerator::hash);

        assertThat(hashes.distinct().count()).isEqualTo(2);
    }
}
