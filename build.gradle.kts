import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType

plugins {
  java
  id("org.jetbrains.intellij.platform")
  id("org.jetbrains.gradle.plugin.idea-ext") version "1.1.9"

//  id("org.jetbrains.intellij") version "1.17.4"
}

// Set the JVM language level used to build the project.
java {
  toolchain {
    languageVersion.set(JavaLanguageVersion.of(17))
  }
}

group = "org.ton.mylocalton.plugin"
version = "1.0-SNAPSHOT"

dependencies {
  intellijPlatform {
    create(IntelliJPlatformType.IntellijIdeaUltimate, "2024.3.5") // community
  }
}
