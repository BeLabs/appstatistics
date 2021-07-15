plugins {
  kotlin("jvm")
  kotlin("plugin.serialization")
  application
  id("com.github.johnrengelman.shadow") version Versions.shadowJar
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
  api(project(":core"))
}

dependencies {
  testImplementation("org.jetbrains.kotlin:kotlin-test-junit")
}
