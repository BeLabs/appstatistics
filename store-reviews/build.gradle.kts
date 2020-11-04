plugins {
  kotlin("jvm")
  kotlin("plugin.serialization")
  application
  id("com.github.johnrengelman.shadow") version "6.1.0"
}

application {
  applicationName = "store-reviews"
  mainClassName = "de.belabs.appstatistics.storereviews.MainKt"
}

defaultTasks("run")

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
  kotlinOptions {
    jvmTarget = "1.8"
  }
}

dependencies {
  api(project(":notifier-slack"))
  api(project(":notifier-telegram-bot"))
}

dependencies {
  implementation("com.github.ajalt:clikt:2.8.0")

  implementation("com.google.apis:google-api-services-androidpublisher:v3-rev20201022-1.30.10")
  implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.0.1")

  val ktor = "1.4.1"
  implementation("io.ktor:ktor-client-core-jvm:$ktor")
  implementation("io.ktor:ktor-client-okhttp:$ktor")
}

dependencies {
  testImplementation("org.jetbrains.kotlin:kotlin-test-junit")
}
