pluginManagement {
    providers.gradleProperty("armalib.buildLogic").orNull?.let { includeBuild(it) }
    val armalibVersion = providers.gradleProperty("armalib_version").get()
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://repo.armadeus.dev/snapshots") {
            content { includeGroupByRegex("dev\\.armadeus.*|armalib\\..*") }
        }
        maven("https://repo.armadeus.dev/releases") {
            content { includeGroupByRegex("dev\\.armadeus.*|armalib\\..*") }
        }
        maven("https://maven.neoforged.net/releases/")
        maven("https://maven.fabricmc.net/")
        maven("https://maven.kikugie.dev/releases")
    }
    plugins {
        id("armalib.mod") version armalibVersion
        id("armalib.bundle") version armalibVersion
        id("armalib.jarcheck") version armalibVersion
        id("armalib.rig") version armalibVersion
        id("me.modmuss50.mod-publish-plugin") version "2.2.1"
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
    id("dev.kikugie.stonecutter") version "0.9.8"
    id("dev.kikugie.loom-back-compat") version "0.4.3"
}

rootProject.name = "Soundstage"

stonecutter {
    create(rootProject) {
        version("1.21.1-neoforge", "1.21.1")
        version("1.21.1-fabric", "1.21.1")
        version("1.21.11-neoforge", "1.21.11")
        version("1.21.11-fabric", "1.21.11")
        version("26.1-neoforge", "26.1")
        version("26.1-fabric", "26.1")
        version("26.2-neoforge", "26.2")
        version("26.2-fabric", "26.2")
        version("26.3-neoforge", "26.3")
        version("26.3-fabric", "26.3")
        version("1.20.1-forge", "1.20.1")
        version("1.20.1-fabric", "1.20.1")
        vcsVersion = "1.21.1-neoforge"
    }
}

include("core")
