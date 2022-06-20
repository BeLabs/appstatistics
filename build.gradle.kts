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
  id("com.vanniktech.code.quality.tools") version "0.21.0"
}

rootProject.configure<com.vanniktech.code.quality.tools.CodeQualityToolsPluginExtension> {
  checkstyle {
    enabled = false // Kotlin only.
  }
  pmd {
    enabled = false // Kotlin only.
  }
  ktlint {
    toolVersion = "0.46.0"
    experimental = true
  }
  detekt {
    enabled = false // Don't want this.
  }
  cpd {
    enabled = false // Kotlin only.
  }
  lint {
    checkAllWarnings = true
    textReport = true // https://github.com/vanniktech/gradle-code-quality-tools-plugin/pull/227
  }
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
      "-opt-in=kotlin.RequiresOptIn"
    )
  }

  tasks.withType(Test::class.java).all {
    testLogging.exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
  }
}

tasks.named<Wrapper>("wrapper") {
  gradleVersion = "7.4.2"
  distributionType = Wrapper.DistributionType.ALL
}
