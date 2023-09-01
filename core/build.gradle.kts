plugins {
  kotlin("jvm")
  kotlin("plugin.serialization")
}

kotlin {
  jvmToolchain {
    languageVersion.set(JavaLanguageVersion.of(libs.versions.java.get().toInt()))
  }
}

dependencies {
  api(libs.androidpublisher)
  api(libs.clikt)
  api(libs.kotlinx.datetime)
  api(libs.kotlinx.serialization.json)
  api(libs.ktor.client.core.jvm)
  api(libs.ktor.client.okhttp)
  api(libs.multiplatform.locale)
}

dependencies {
  testImplementation(libs.kotlin.test.junit)
}
