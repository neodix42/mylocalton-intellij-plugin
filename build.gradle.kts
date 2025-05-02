import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType

plugins {
  java
  id("org.jetbrains.intellij.platform")
//  id("org.jetbrains.intellij") version "1.17.4"

}

// Set the JVM language level used to build the project.
java {
  toolchain {
    languageVersion.set(JavaLanguageVersion.of(21))
  }
}

group = "org.ton.mylocalton.plugin"
version = "1.0-SNAPSHOT"

dependencies {
  intellijPlatform {
    create(IntelliJPlatformType.IntellijIdeaUltimate, "2024.3.5") // community
  }
  implementation("commons-io:commons-io:2.19.0")
  implementation("io.github.neodix42:liteclient:0.9.7")
  implementation("com.googlecode.json-simple:json-simple:1.1.1")
}
