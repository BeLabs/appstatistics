plugins {
  kotlin("jvm")
  kotlin("plugin.serialization")
}

kotlin {
  jvmToolchain {
    languageVersion.set(JavaLanguageVersion.of(18))
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
