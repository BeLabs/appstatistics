buildscript {
  repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
  }
  dependencies {
    classpath(libs.plugin.kotlin)
    classpath(libs.plugin.kotlin.serialization)
    classpath(libs.plugin.shadowjar)
  }
}

plugins {
  alias(libs.plugins.codequalitytools)
}

rootProject.configure<com.vanniktech.code.quality.tools.CodeQualityToolsPluginExtension> {
  checkstyle {
    enabled = false // Kotlin only.
  }
  pmd {
    enabled = false // Kotlin only.
  }
  ktlint {
    toolVersion = libs.versions.ktlint.get()
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
  gradleVersion = libs.versions.gradle.get()
  distributionType = Wrapper.DistributionType.ALL
}
