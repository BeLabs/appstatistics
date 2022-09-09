pluginManagement {
  repositories {
    mavenCentral()
    google()
    gradlePluginPortal()
    mavenLocal()
  }
}

enableFeaturePreview("VERSION_CATALOGS")

include(":core")
include(":inapp-products")
include(":notifier")
include(":notifier-slack")
include(":notifier-telegram-bot")
include(":store-reviews")
