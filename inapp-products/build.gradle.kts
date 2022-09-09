plugins {
  application
  id("com.github.johnrengelman.shadow")
  kotlin("jvm")
  kotlin("plugin.serialization")
}

kotlin {
  jvmToolchain {
    languageVersion.set(JavaLanguageVersion.of(18))
  }
}

dependencies {
  api(project(":core"))
}

dependencies {
  testImplementation(libs.kotlin.test.junit)
}

application {
  applicationName = "inapp-products"
  mainClass.set("de.belabs.appstatistics.inappproducts.MainKt")
}
