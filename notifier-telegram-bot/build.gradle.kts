plugins {
  kotlin("jvm")
  kotlin("plugin.serialization")
}

dependencies {
  api(project(":notifier"))
}

dependencies {
  testImplementation(libs.kotlin.test.junit)
}
