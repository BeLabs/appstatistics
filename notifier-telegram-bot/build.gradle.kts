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
  api(project(":notifier"))
}

dependencies {
  testImplementation(libs.kotlin.test.junit)
}
