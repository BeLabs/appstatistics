buildscript {
  repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
  }
  dependencies {
    classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:${Versions.kotlinVersion}")
    classpath("org.jetbrains.kotlin:kotlin-serialization:${Versions.kotlinVersion}")
  }
}

plugins {
  id("com.github.ben-manes.versions") version "0.39.0"
}

allprojects {
  repositories {
    google()
    mavenCentral()
  }
}

subprojects {
  tasks.withType(org.jetbrains.kotlin.gradle.tasks.KotlinCompile::class.java).all {
    kotlinOptions.freeCompilerArgs += listOf(
      "-Xuse-experimental=kotlin.Experimental",
      "-Xopt-in=kotlin.RequiresOptIn"
    )
  }
}

tasks.named<Wrapper>("wrapper") {
  gradleVersion = "7.1.1"
  distributionType = Wrapper.DistributionType.ALL
}
