import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.changelog.Changelog

import java.time.Clock
import java.time.Instant

val publishChannel = prop("publishChannel")
val pluginVersion = prop("pluginVersion").let { pluginVersion ->
  if (publishChannel != "release" && publishChannel != "stable") {
    val buildSuffix = prop("buildNumber") {
      Instant.now(Clock.systemUTC()).toString().substring(2, 16).replace("[-T:]".toRegex(), "")
    }
    "$pluginVersion-${publishChannel.uppercase()}+$buildSuffix"
  } else {
    pluginVersion
  }
}
version = pluginVersion

plugins {
  java
  id("org.jetbrains.intellij.platform")
  id("org.jetbrains.changelog") version "2.2.1"
}

// Set the JVM language level used to build the project.
java {
  toolchain {
    languageVersion.set(JavaLanguageVersion.of(21))
  }
}

dependencies {
  intellijPlatform {
    val version = providers.gradleProperty("platformVersion")
    create(IntelliJPlatformType.IntellijIdeaCommunity, version)  }
  implementation("commons-io:commons-io:2.19.0")
  implementation("io.github.neodix42:liteclient:0.9.7")
  implementation("com.googlecode.json-simple:json-simple:1.1.1")
}


intellijPlatform {
  pluginConfiguration {
    id = "org.ton.intellij-ton"
    name = "TON"
    version = project.version.toString()
    description = """
        TON Blockchain inside IntelliJ: Allows to run local TON blockchain with native explorer and TON HTTP API.
        Ideal for quick smart-contract development using third-party TON libraries such ton-kotlin or ton4j.
        """.trimIndent()
    changeNotes.set(
      provider {
        changelog.renderItem(changelog.getLatest(), Changelog.OutputType.HTML)
      }
    )
    ideaVersion {
      sinceBuild.set("241")
      untilBuild = provider { null }
    }
    vendor {
      name = "TON Core"
      url = "https://github.com/neodix42/mylocalton-intellij-plugin"
      email = "neodix@ton.org"
    }
  }
}

changelog {
  version.set(version)
  path.set("${project.projectDir}/CHANGELOG.md")
  header.set(provider { "[${version.get()}]" })
  itemPrefix.set("-")
  keepUnreleasedSection.set(true)
  unreleasedTerm.set("[Unreleased]")
  groups.set(listOf("Added", "Changed", "Deprecated", "Removed", "Fixed", "Security"))
}

fun prop(name: String, default: (() -> String?)? = null) = extra.properties[name] as? String
  ?: default?.invoke() ?: error("Property `$name` is not defined in gradle.properties")