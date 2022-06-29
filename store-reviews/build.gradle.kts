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

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
  kotlinOptions {
    jvmTarget = "1.8"
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
