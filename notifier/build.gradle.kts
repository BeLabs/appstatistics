plugins {
  kotlin("jvm")
}

dependencies {
  api(libs.kotlinx.serialization.json)
  api(libs.ktor.client.core.jvm)
  api(libs.ktor.client.okhttp)
}

dependencies {
  testImplementation(libs.kotlin.test.junit)
}
