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

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

/**
 * logical-components.md §5。{@code @EnableCaching}を{@code MasterMeisterApplication}から
 * 独立した{@code @Configuration}クラスに切り出す。メインクラスに直接付与すると、
 * {@code @DataJpaTest}等のテストスライスがCacheAutoConfigurationを除外する一方でこの
 * アノテーション自体はルート設定クラスとして読み込まれてしまい、CacheManagerが
 * 存在せず起動に失敗する（実装時に発見）。通常の{@code @Configuration}クラスとして
 * 切り出すことで、テストスライスのコンポーネントスキャン除外対象となり問題を回避する。
 */
@Configuration
@EnableCaching
public class CacheConfig {
}
