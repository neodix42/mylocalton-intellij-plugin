import org.jetbrains.intellij.platform.gradle.extensions.intellijPlatform

plugins {
    id("org.jetbrains.intellij.platform.settings") version "2.5.0"
}

rootProject.name = "mylocalton-intellij-plugin"
gradle.startParameter.showStacktrace = ShowStacktrace.ALWAYS_FULL

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS

    repositories {
        mavenCentral()
        intellijPlatform {
            defaultRepositories()
        }

//        maven {
//            url = uri("C:\\\\Users\\\\namle\\\\.m2\\\\repository")
//        }
    }
}
