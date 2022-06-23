plugins {
    id("javelin.base-conventions")
    id("javelin.mindustry-conventions")
}

val relocate = tasks.create<com.github.jengelman.gradle.plugins.shadow.tasks.ConfigureShadowRelocation>("relocateShadowJar") {
    target = tasks.shadowJar.get()
    prefix = "fr.xpdustry.javelin.plugin.shadow"
}

tasks.shadowJar.get().dependsOn(relocate)
