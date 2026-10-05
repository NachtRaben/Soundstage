plugins {
    `java-library`
}

group = providers.gradleProperty("mod_group_id").get()
version = providers.gradleProperty("mod_version").get()

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

tasks.named<JavaCompile>("compileJava") {
    options.release.set(17)
}

repositories {
    mavenCentral()
    if (providers.gradleProperty("armalib.local").isPresent) {
        mavenLocal { content { includeGroup("dev.armadeus") } }
    }
    maven("https://repo.armadeus.dev/snapshots") {
        content { includeGroup("dev.armadeus") }
    }
    maven("https://repo.armadeus.dev/releases") {
        content { includeGroup("dev.armadeus") }
    }
}

val armalibVersion = providers.gradleProperty("armalib_version").get()

dependencies {
    compileOnly(libs.armalib.lavaplayer) { version { require(armalibVersion) } }
    compileOnly(libs.armalib.lavaplayer.youtube) { version { require(armalibVersion) } }

    compileOnly(libs.slf4j.api)

    compileOnly(libs.jcodec)

    compileOnly(libs.xz)

    testImplementation(libs.armalib.lavaplayer) { version { require(armalibVersion) } }
    testImplementation(libs.armalib.lavaplayer.youtube) { version { require(armalibVersion) } }
    testImplementation(libs.slf4j.api)
    testRuntimeOnly(libs.commons.io)
    testImplementation(libs.jcodec)
    testImplementation(libs.xz)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}
