// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    id("com.android.application") version "8.9.1" apply false
    id("org.jetbrains.kotlin.android") version "2.2.10" apply false
    id("org.jlleitschuh.gradle.ktlint") version "13.1.0" apply false
    id("io.gitlab.arturbosch.detekt") version "1.23.8" apply false
}

allprojects {
    configurations.all {
        resolutionStrategy {
            // Cache dynamic versions for 10 minutes
            cacheDynamicVersionsFor(10, "minutes")
            // Cache changing modules for 10 minutes
            cacheChangingModulesFor(10, "minutes")
        }
    }
}

tasks.register("clean", Delete::class) {
    delete(rootProject.buildDir)
}