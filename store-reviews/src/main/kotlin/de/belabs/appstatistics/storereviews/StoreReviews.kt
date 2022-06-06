package de.belabs.appstatistics.storereviews

import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import de.belabs.appstatistics.CoreCommand
import de.belabs.appstatistics.jsonPretty
import de.belabs.appstatistics.storereviews.store.AppleStore
import de.belabs.appstatistics.storereviews.store.PlayStore
import de.belabs.appstatistics.storereviews.store.Store
import de.belabs.notifier.slack.SlackNotifier
import de.belabs.notifier.telegrambot.TelegramBotNotifier
import kotlinx.serialization.builtins.ListSerializer
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Locale

internal class StoreReviews : CoreCommand() {
  private val locale: Locale by option(help = "Locale")
    .convert { Locale(it) }
    .default(Locale.getDefault())

  private val timeZone: ZoneId by option(help = "Time zone")
    .convert { ZoneId.of(it) }
    .default(ZoneId.systemDefault())

  override suspend fun runOn(organization: String, root: File) {
    val directory = root.resolve("store-reviews/")
    directory.mkdirs()

    // Validate apps.
    val appsFile = directory.resolve("apps.json")

    if (!appsFile.exists()) {
      logger.log("""🔴 $appsFile is missing. Please configure your apps and re-run this script.""")
      logger.log("Here's an example configuration:")
      logger.log(jsonPretty.encodeToString(ListSerializer(App.serializer()), listOf(App.EXAMPLE)))
      return
    }

    val appsJson = appsFile.takeIf { it.exists() }?.readText() ?: "[]"
    val apps = jsonPretty.decodeFromString(ListSerializer(App.serializer()), appsJson)

    if (apps.isEmpty()) {
      logger.log("""⚠️ $appsFile contains no apps""")
    }

    // Validate Play Store file.
    val hasAndroidApps = apps.count { it.androidPackageName?.isNotBlank() == true } > 0
    val playStoreFile = when {
      hasAndroidApps -> playStoreFile(root) ?: return
      else -> null
    }

    // Validate notifiers.
    val reviewFormatter = ReviewFormatter(locale, timeZone)
    val slackConfigurationFile = directory.resolve("slack.json")
    val slackNotifierConfiguration = slackConfigurationFile.takeIf { it.exists() }
      ?.readText()
      ?.let { jsonPretty.decodeFromString(JsonSlackNotifierConfiguration.serializer(), it) }

    val telegramBotConfigurationFile = directory.resolve("telegram_bot.json")
    val telegramBotNotifierConfiguration = telegramBotConfigurationFile.takeIf { it.exists() }
      ?.readText()
      ?.let { jsonPretty.decodeFromString(JsonTelegramBotNotifierConfiguration.serializer(), it) }

    val notifiers = StoreReviewsNotifier(
      directory = directory.resolve(".notifiers"),
      reviewFormatter = reviewFormatter,
      slackNotifierConfiguration = slackNotifierConfiguration,
      telegramBotNotifierConfiguration = telegramBotNotifierConfiguration,
    )

    if (notifiers.isEmpty()) {
      logger.log("""🔴 No notifiers configured for $organization. We support the following:""")
      logger.increaseIndent()

      val supportedNotifiers = mapOf(
        slackConfigurationFile to SlackNotifier(JsonSlackNotifierConfiguration.EXAMPLE),
        telegramBotConfigurationFile to TelegramBotNotifier(JsonTelegramBotNotifierConfiguration.EXAMPLE)
      )

      supportedNotifiers.forEach { (file, notifier) ->
        logger.log("""${notifier.emoji()} ${notifier.name()} - Configure via $file - example:""")
        logger.log(notifier.configuration.asString(jsonPretty))
      }
      return
    }

    // Stores.
    val stores = listOfNotNull(
      playStoreFile?.let { PlayStore(it) },
      AppleStore(),
    )

    process(
      directory = directory,
      apps = apps,
      stores = stores,
      notifiers = notifiers,
    )
  }

  private suspend fun process(directory: File, apps: List<App>, stores: List<Store>, notifiers: StoreReviewsNotifier) {
    apps.forEach { app ->
      stores.forEach { store ->
        logger.log("""⭐ Querying ${store.name()} ${app.name} reviews""")
        logger.increaseIndent()

        val appOutput = directory.resolve("${app.name}/${store.name()}")
        appOutput.mkdirs()

        val unnotifiedReviews = notifiers.missing(
          appOutput.listFiles()
            .orEmpty()
            .filter { it.name.endsWith(".json") }
            .map { it.nameWithoutExtension }
        )
          .map { jsonPretty.decodeFromString(Review.serializer(), appOutput.resolve("$it.json").readText()) }
          .filter { notifiers.canNotify(it) && it.updated >= Instant.now().minus(10, ChronoUnit.DAYS) }

        val reviews = store.reviews(app)
          .sortedBy { it.updated }
          .mapNotNull { review ->
            val file = appOutput.resolve("${review.id}.json")

            if (!file.exists() || file.length() == 0L) {
              file.writeText(jsonPretty.encodeToString(Review.serializer(), review))
              review
            } else {
              null
            }
          }

        // Unnotified Reviews.
        if (unnotifiedReviews.isNotEmpty()) {
          logger.log("""⚠️ ${unnotifiedReviews.size} unnotified reviews""")
          logger.increaseIndent()
          notifiers.notify(logger, app, store.name(), unnotifiedReviews)
          logger.decreaseIndent()
        }

        // New Reviews.
        val reviewsEmoji = when {
          reviews.isEmpty() -> """😔"""
          else -> """💌"""
        }
        logger.log("""$reviewsEmoji ${reviews.size} new reviews""")

        if (reviews.isNotEmpty()) {
          logger.increaseIndent()
          notifiers.notify(logger, app, store.name(), reviews)
          logger.decreaseIndent()
        }

        logger.log()
        logger.decreaseIndent()
      }
    }
  }
}
