plugins {
    java
    war
    id("org.springframework.boot") version "4.1.0"
    id("io.spring.dependency-management") version "1.1.7"
    id("org.owasp.dependencycheck") version "12.1.0"
}

group = "cherry"
version = "0.0.1-SNAPSHOT"
description = "MasterMeister backend"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-restclient")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-mail")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-flyway")
    // UNIT-04: 実効権限キャッシュ(tech-stack-decisions.md §1・§2)
    implementation("org.springframework.boot:spring-boot-starter-cache")
    implementation("com.github.ben-manes.caffeine:caffeine:3.2.4")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.5")
    implementation(project(":cherry-mustache-core"))
    runtimeOnly("com.h2database:h2")
    runtimeOnly("org.flywaydb:flyway-core")
    runtimeOnly("net.logstash.logback:logstash-logback-encoder:8.0")
    // UNIT-03: 対象RDBMS接続用JDBCドライバ(tech-stack-decisions.md §8)
    runtimeOnly("com.mysql:mysql-connector-j:9.7.0")
    runtimeOnly("org.mariadb.jdbc:mariadb-java-client:3.5.9")
    runtimeOnly("org.postgresql:postgresql:42.7.13")
    providedRuntime("org.springframework.boot:spring-boot-starter-tomcat-runtime")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("net.jqwik:jqwik:1.9.1")
    // UNIT-03: SchemaIntrospectionServiceTestで対象RDBMS役のH2 TCPサーバを起動するため
    // (org.h2.tools.Server)、テストコンパイル時にも明示的に依存を追加する
    testImplementation("com.h2database:h2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

tasks.war {
    archiveBaseName.set("mastermeister")
}

// bootWarはfrontendのビルド成果物(dist/)をstaticリソースとして内包する。
// :backend:build / :backend:assemble の通常フローには影響させず(frontendの
// 影響を受けないバックエンド単体ビルドを維持するため)、リリースビルド時に
// `./gradlew :backend:bootWar` を明示的に実行した場合のみfrontendを巻き込む。
// static配下はSpring Bootの静的リソース解決規約(classpath:/static/)に従うため、
// WEB-INF/classes/static/ へ配置する。
tasks.bootWar {
    archiveBaseName.set("mastermeister")
    dependsOn(":frontend:npmBuild")
    from(project(":frontend").file("dist")) {
        into("WEB-INF/classes/static")
    }
}

tasks.named("assemble") {
    setDependsOn(emptyList<Any>())
}
