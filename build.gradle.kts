plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
    alias(libs.plugins.ktlint)
}

group = "com.octoberdiscussion"
version = "0.1.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.spring.boot.starter.webflux)
    implementation(libs.spring.boot.starter.jdbc)
    implementation(libs.jackson.module.kotlin)
    implementation(libs.kotlinx.coroutines.reactor)
    implementation(libs.kotlin.logging)
    implementation(libs.sqlite.jdbc)
    implementation(libs.bcrypt)
    implementation(libs.commonmark)
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    testImplementation(libs.spring.boot.starter.test) {
        exclude(group = "org.mockito")
    }
    testImplementation(libs.reactor.test)
    testImplementation(libs.mockk)
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
}
