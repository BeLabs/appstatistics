plugins {
  kotlin("jvm")
  kotlin("plugin.serialization")
  application
  id("com.github.johnrengelman.shadow") version "5.0.0"
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
  implementation("com.github.ajalt:clikt:2.8.0")

  implementation("com.google.apis:google-api-services-androidpublisher:v3-rev20200817-1.30.10")
  implementation("org.jetbrains.kotlinx:kotlinx-serialization-runtime:0.20.0")

  val ktor = "1.3.2"
  implementation("io.ktor:ktor-client-core-jvm:$ktor")
  implementation("io.ktor:ktor-client-logging-jvm:$ktor")
  implementation("io.ktor:ktor-client-okhttp:$ktor")

  testImplementation("org.jetbrains.kotlin:kotlin-test-junit")
}
