plugins {
  kotlin("jvm")
  kotlin("plugin.serialization")
  application
  id("com.github.johnrengelman.shadow") version "6.1.0"
}

application {
  applicationName = "inapp-products"
  mainClassName = "de.belabs.appstatistics.inappproducts.MainKt"
}

defaultTasks("run")

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
  kotlinOptions {
    jvmTarget = "1.8"
  }
}

dependencies {
  api("com.github.ajalt:clikt:2.8.0")

  val ktor = "1.4.1"
  api("io.ktor:ktor-client-core-jvm:$ktor")
  api("io.ktor:ktor-client-okhttp:$ktor")

  api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.0.1")

  api("com.google.apis:google-api-services-androidpublisher:v3-rev20201022-1.30.10")
}

dependencies {
  testImplementation("org.jetbrains.kotlin:kotlin-test-junit")
}
