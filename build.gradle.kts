buildscript {
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
  }
  cpd {
    enabled = false // Kotlin only.
  }
  lint {
    checkAllWarnings = true
  }
}

allprojects {
  repositories {
    google()
    mavenCentral()
  }
}

subprojects {
  tasks.withType(Test::class.java).all {
    testLogging.exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
  }
}
