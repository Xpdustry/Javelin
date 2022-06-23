plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
    maven("https://repo.xpdustry.fr/releases") {
        name = "xpdustry-releases"
        mavenContent { releasesOnly() }
    }
}

dependencies {
    implementation("net.kyori:indra-common:2.1.1")
    implementation("gradle.plugin.com.github.johnrengelman:shadow:7.1.2")
    implementation("fr.xpdustry:toxopid:2.0.0")
    implementation("net.ltgt.gradle:gradle-errorprone-plugin:2.0.2")
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}
