plugins {
  kotlin("jvm")
  kotlin("plugin.serialization")
}

dependencies {
  api(project(":notifier"))
}

dependencies {
  implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.0.1")

  val ktor = "1.4.1"
  implementation("io.ktor:ktor-client-core-jvm:$ktor")
  implementation("io.ktor:ktor-client-okhttp:$ktor")
}

dependencies {
  testImplementation("org.jetbrains.kotlin:kotlin-test-junit")
}
