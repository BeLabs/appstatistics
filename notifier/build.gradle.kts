plugins {
  kotlin("jvm")
}

kotlin {
  jvmToolchain {
    languageVersion.set(JavaLanguageVersion.of(libs.versions.java.get().toInt()))
  }
}

dependencies {
  api(libs.kotlinx.serialization.json)
  api(libs.ktor.client.core.jvm)
  api(libs.ktor.client.okhttp)
}

dependencies {
  testImplementation(libs.kotlin.test.junit)
}
