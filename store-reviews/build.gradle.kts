plugins {
  application
  id("com.github.johnrengelman.shadow")
  kotlin("jvm")
  kotlin("plugin.serialization")
}

kotlin {
  jvmToolchain {
    languageVersion.set(JavaLanguageVersion.of(libs.versions.java.get().toInt()))
  }
}

dependencies {
  api(project(":core"))
  api(project(":notifier-slack"))
  api(project(":notifier-telegram-bot"))
}

dependencies {
  testImplementation(libs.kotlin.test.junit)
}

application {
  applicationName = "store-reviews"
  mainClass.set("de.belabs.appstatistics.storereviews.MainKt")
}
