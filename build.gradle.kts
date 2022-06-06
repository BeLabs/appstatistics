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
}

tasks.named<Wrapper>("wrapper") {
  gradleVersion = "7.4.2"
  distributionType = Wrapper.DistributionType.ALL
}
