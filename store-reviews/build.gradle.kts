plugins {
  kotlin("jvm")
  kotlin("plugin.serialization")
  application
  id("com.github.johnrengelman.shadow")
}

application {
  applicationName = "store-reviews"
  mainClass.set("de.belabs.appstatistics.storereviews.MainKt")
}

defaultTasks("run")

kotlin {
  jvmToolchain {
    languageVersion.set(JavaLanguageVersion.of(18))
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
