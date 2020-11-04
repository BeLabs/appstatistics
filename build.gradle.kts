buildscript {
  repositories {
    google()
    mavenCentral()
    jcenter()
    gradlePluginPortal()
  }
  dependencies {
    val kotlinVersion = "1.4.10"
    classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
    classpath("org.jetbrains.kotlin:kotlin-serialization:$kotlinVersion")
  }
}

plugins {
  id("com.github.ben-manes.versions") version "0.34.0"
}

allprojects {
  repositories {
    google()
    mavenCentral()
    jcenter()
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
  gradleVersion = "6.5.1"
  distributionType = Wrapper.DistributionType.ALL
}
