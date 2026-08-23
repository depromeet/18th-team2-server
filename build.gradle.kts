plugins {
    kotlin("jvm") version "2.3.0"
    kotlin("plugin.spring") version "2.3.0"
    id("org.springframework.boot") version "4.0.5"
    id("io.spring.dependency-management") version "1.1.7"
    kotlin("plugin.jpa") version "2.3.0"
    id("org.jlleitschuh.gradle.ktlint") version "12.3.0"
    id("dev.detekt") version "2.0.0-alpha.2"
    jacoco
}

group = "com.team2"
version = "0.0.1-SNAPSHOT"
description = "server"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-websocket")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("tools.jackson.module:jackson-module-kotlin")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("io.micrometer:micrometer-registry-prometheus")
    implementation("com.github.loki4j:loki-logback-appender:1.5.2")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.3")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    developmentOnly("org.springframework.boot:spring-boot-devtools")
    runtimeOnly("org.flywaydb:flyway-mysql")
    runtimeOnly("com.mysql:mysql-connector-j")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
    testImplementation("org.springframework.boot:spring-boot-starter-validation-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.4.0")
    testImplementation("com.tngtech.archunit:archunit-junit5:1.4.0")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-mysql")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
    }
}

allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}

tasks.bootJar {
    archiveFileName.set("app.jar")
}

tasks.jar {
    enabled = false
}

tasks.processResources {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from("config/secret") {
        include("*.yml", "*.yaml")
        into("")
    }
}

ktlint {
    version.set("1.6.0")
}

tasks.withType<Test> {
    useJUnitPlatform()
    systemProperty("user.timezone", "Asia/Seoul")
    environment("TESTCONTAINERS_RYUK_DISABLED", "true")
    finalizedBy(tasks.jacocoTestReport)
}

tasks.withType<org.springframework.boot.gradle.tasks.run.BootRun> {
    systemProperty("user.timezone", "Asia/Seoul")
    environment("TZ", "Asia/Seoul")
    environment("APP_TIME_ZONE", System.getenv("APP_TIME_ZONE") ?: "Asia/Seoul")
}

tasks.jacocoTestReport {
    reports {
        xml.required = true
        html.required = true
    }
}
