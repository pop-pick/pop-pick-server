import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.kotlin.jpa)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
    alias(libs.plugins.ktlint)
}

val jdkVersion: String = libs.versions.jdk.get()

group = providers.gradleProperty("projectGroup").get()
version = providers.gradleProperty("applicationVersion").get()
description = "poppick"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(jdkVersion)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation(libs.kotlin.reflect)
    implementation(libs.jackson.module.kotlin)

    // db
    runtimeOnly(libs.postgresql)
    runtimeOnly(libs.h2)
    runtimeOnly(libs.mysql.connector.j)

    implementation(libs.querydsl.core)
    implementation(libs.querydsl.jpa)
    kapt(variantOf(libs.querydsl.apt) { classifier("jpa") })

    // security
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
    implementation(libs.jjwt.api)
    runtimeOnly(libs.jjwt.impl)
    runtimeOnly(libs.jjwt.jackson)

    // Redis
    implementation("org.springframework.boot:spring-boot-starter-data-redis")

    // Metric
    implementation(libs.spring.boot.starter.actuator)
    runtimeOnly(libs.micrometer.registry.prometheus)

    // Api Docs
    implementation(libs.springdoc.openapi)

    // test
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.mockk)
    testRuntimeOnly(libs.junit.platform.launcher)
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
        jvmTarget.set(JvmTarget.fromTarget(jdkVersion))
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// Querydsl Settings
val kaptGeneratedDir = "build/generated/source/kapt/main"

kapt {
    keepJavacAnnotationProcessors = true
    arguments {
        arg("querydsl.entityAccessors", "true")
    }
}

sourceSets {
    main {
        java.srcDirs(kaptGeneratedDir)
    }
}

tasks.named("clean") {
    doLast {
        file(kaptGeneratedDir).deleteRecursively()
    }
}

// Fix ktlint task dependency on kapt
tasks.matching { it.name.startsWith("runKtlintCheck") }.configureEach {
    dependsOn("kaptKotlin")
}
