plugins {
    java
    id("org.springframework.boot") version "3.3.5"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com"
version = "0.0.1-SNAPSHOT"
description = "ecom-assistant"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

repositories {
    mavenCentral()
    maven { url = uri("https://repo.spring.io/milestone") }
    maven { url = uri("https://repo.spring.io/snapshot") }
    maven { url = uri("https://central.sonatype.com/repository/maven-snapshots/") }
}

dependencies {
    implementation(platform("org.springframework.ai:spring-ai-bom:1.1.0-20250919.113626-495"))
//    implementation(platform("org.springframework.ai:spring-ai-bom:1.1.0-M1-PLATFORM-2"))
    implementation("org.springframework.ai:spring-ai-autoconfigure-model-openai")
//    implementation("org.springframework.ai:spring-ai-starter-model-ollama")
    implementation("org.springframework.ai:spring-ai-rag")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
    implementation("com.mysql:mysql-connector-j:8.2.0")
    implementation("org.apache.commons:commons-csv:1.10.0")

    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
    implementation("org.apache.commons:commons-lang3:3.14.0")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.testcontainers:mysql")
    testImplementation("org.testcontainers:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
