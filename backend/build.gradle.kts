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
    providedRuntime("org.springframework.boot:spring-boot-starter-tomcat-runtime")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
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

