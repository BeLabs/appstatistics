plugins {
  kotlin("jvm")
  kotlin("plugin.serialization")
  application
  id("com.github.johnrengelman.shadow") version Versions.shadowJar
}

application {
  applicationName = "inapp-products"
  mainClass.set("de.belabs.appstatistics.inappproducts.MainKt")
}

defaultTasks("run")

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
  kotlinOptions {
    jvmTarget = "1.8"
  }
}

dependencies {
  api("com.github.ajalt:clikt:${Versions.clikt}")

  api("io.ktor:ktor-client-core-jvm:${Versions.ktor}")
  api("io.ktor:ktor-client-okhttp:${Versions.ktor}")

  api("org.jetbrains.kotlinx:kotlinx-serialization-json:${Versions.kotlinSerialization}")

  api("com.google.apis:google-api-services-androidpublisher:v3-rev20201022-1.30.10")
}

dependencies {
  testImplementation("org.jetbrains.kotlin:kotlin-test-junit")
}
