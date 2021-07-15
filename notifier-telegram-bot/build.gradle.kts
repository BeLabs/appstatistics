plugins {
  kotlin("jvm")
  kotlin("plugin.serialization")
}

dependencies {
  api(project(":notifier"))
}

dependencies {
  implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:${Versions.kotlinSerialization}")
  implementation("io.ktor:ktor-client-core-jvm:${Versions.ktor}")
  implementation("io.ktor:ktor-client-okhttp:${Versions.ktor}")
}

dependencies {
  testImplementation("org.jetbrains.kotlin:kotlin-test-junit")
}
