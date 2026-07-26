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

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;
import org.springframework.context.annotation.Configuration;

/**
 * spring-boot-starter-opentelemetryが構成するSdkLoggerProvider/OTLPエクスポータは、
 * Logbackのログイベントをそこへ転送するアペンダを含まない（Spring Bootの自動構成範囲外）。
 * このため{@code logback-spring.xml}で宣言した{@link OpenTelemetryAppender}に対して、
 * 起動時にSpring管理下の{@link OpenTelemetry}インスタンスを明示的にインストールする。
 * インストール前に出力されたログはOTLPへは転送されない（アペンダ側の仕様）。
 */
@Configuration
public class OpenTelemetryLoggingConfig {

    public OpenTelemetryLoggingConfig(OpenTelemetry openTelemetry) {
        OpenTelemetryAppender.install(openTelemetry);
    }
}
