import fr.xpdustry.toxopid.extension.ModDependency
import fr.xpdustry.toxopid.util.ModMetadata
import fr.xpdustry.toxopid.extension.ModTarget
import java.io.ByteArrayOutputStream

plugins {
    kotlin("jvm") version "1.6.10"
    id("net.kyori.indra") version "2.1.1"
    id("net.kyori.indra.publishing") version "2.1.1"
    id("fr.xpdustry.toxopid") version "1.3.2"
    id("com.github.ben-manes.versions") version "0.42.0"
}

val metadata = ModMetadata(file("${rootProject.rootDir}/plugin.json"))
group = property("props.project-group").toString()
version = metadata.version + if (indraGit.headTag() == null) "-SNAPSHOT" else ""

toxopid {
    modTarget.set(ModTarget.HEADLESS)
    arcCompileVersion.set(metadata.minGameVersion)
    mindustryCompileVersion.set(metadata.minGameVersion)

    mindustryRepository.set(fr.xpdustry.toxopid.extension.MindustryRepository.BE)
    mindustryRuntimeVersion.set("22350")

    modDependencies.set(listOf(
        ModDependency("Xpdustry/Distributor", "v2.6.1", "distributor-core.jar"),
        ModDependency("Xpdustry/KotlinRuntimePlugin", "v1.0.0", "xpdustry-kotlin-stdlib.jar")
    ))
}

repositories {
    mavenCentral()
    maven("https://repo.xpdustry.fr/releases") {
        name = "xpdustry-releases-repository"
        mavenContent {
            includeGroupByRegex("fr\\.xpdustry|net\\.mindustry_ddns")
            releasesOnly()
        }
    }
}

dependencies {
    compileOnly(kotlin("stdlib"))

    val distributor = "2.6.1"
    compileOnly("fr.xpdustry:distributor-core:$distributor")
    testImplementation("fr.xpdustry:distributor-core:$distributor")

    implementation("org.java-websocket:Java-WebSocket:1.5.2")
    implementation("com.google.inject:guice:5.1.0")
    implementation("com.auth0:java-jwt:3.19.1")
    implementation("org.slf4j:slf4j-simple:1.7.36")

    val junit = "5.8.2"
    testImplementation("org.junit.jupiter:junit-jupiter-params:$junit")
    testImplementation("org.junit.jupiter:junit-jupiter-api:$junit")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$junit")
    testImplementation(kotlin("test-junit"))

    val jetbrains = "23.0.0"
    compileOnly("org.jetbrains:annotations:$jetbrains")
    testCompileOnly("org.jetbrains:annotations:$jetbrains")
}

// Required if you want to use the Release GitHub action
tasks.create("getArtifactPath") {
    doLast { println(tasks.shadowJar.get().archiveFile.get().toString()) }
}

tasks.create("createRelease") {
    dependsOn("requireClean")

    doLast {
        // Checks if a signing key is present
        val signing = ByteArrayOutputStream().use { out ->
            exec {
                commandLine("git", "config", "--global", "user.signingkey")
                standardOutput = out
            }.run { exitValue == 0 && out.toString().isNotBlank() }
        }

        exec {
            commandLine(arrayListOf("git", "tag", "v${metadata.version}", "-F", "./CHANGELOG.md", "-a").apply { if (signing) add("-s") })
        }

        exec {
            commandLine("git", "push", "origin", "--tags")
        }
    }
}

tasks.shadowJar {
    val destination = "fr.xpdustry.javelin.internal"
    relocate("com.google.inject", "$destination.guice")
    relocate("com.fasterxml.jackson", "$destination.jackson")
    relocate("org.slf4j", "$destination.slf4j")
    minimize()
}

signing {
    val signingKey: String? by project
    val signingPassword: String? by project
    useInMemoryPgpKeys(signingKey, signingPassword)
}

indra {
    javaVersions {
        target(17)
        minimumToolchain(17)
    }

    publishReleasesTo("xpdustry", "https://repo.xpdustry.fr/releases")
    publishSnapshotsTo("xpdustry", "https://repo.xpdustry.fr/snapshots")

    gpl3OnlyLicense()

    if (metadata.repo != null) {
        val repo = metadata.repo!!.split("/")
        github(repo[0], repo[1]) {
            ci(true)
            issues(true)
            scm(true)
        }
    }

    configurePublications {
        pom {
            developers {
                developer { id.set(metadata.author) }
            }
        }
    }
}
