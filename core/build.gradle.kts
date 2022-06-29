plugins {
  kotlin("jvm")
  kotlin("plugin.serialization")
  application
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
  api(libs.androidpublisher)
  api(libs.clikt)
  api(libs.kotlinx.serialization.json)
  api(libs.ktor.client.core.jvm)
  api(libs.ktor.client.okhttp)
}

dependencies {
  testImplementation(libs.kotlin.test.junit)
}
