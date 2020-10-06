package de.belabs.appstatistics.storereviews

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import de.belabs.appstatistics.storereviews.store.AppleStore
import de.belabs.appstatistics.storereviews.store.PlayStore
import de.belabs.appstatistics.storereviews.store.Store
import de.belabs.notifier.slack.SlackNotifier
import de.belabs.notifier.telegrambot.TelegramBotNotifier
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.time.ZoneId
import java.util.Locale

internal class StoreReviews : CliktCommand() {
  private val directoryAppStatistics: File by option(help = "App statistics directory")
    .convert { File(it) }
    .default(File(System.getProperty("user.home")).resolve(".appstatistics"))

  private val locale: Locale by option(help = "Locale")
    .convert { Locale(it) }
    .default(Locale.getDefault())

  private val timeZone: ZoneId by option(help = "Time zone")
    .convert { ZoneId.of(it) }
    .default(ZoneId.systemDefault())

  @OptIn(ExperimentalSerializationApi::class)
  private val json = Json {
    prettyPrint = true
    prettyPrintIndent = "  "
  }

  private val logger = Logger()

  override fun run() = runBlocking {
    logger.log("""🔎 Scanning for accounts in $directoryAppStatistics""")
    logger.increaseIndent()

    val organizations = directoryAppStatistics.listFiles { current, name ->
      current.resolve(name).isDirectory
    }.orEmpty()
      .filterNot { it.isHidden }

    if (organizations.isNotEmpty()) {
      logger.log("""📒️ Found ${organizations.joinToString { it.name }}""")
      logger.increaseIndent()

      organizations.forEach {
        account(it)
        yield() // Keep the order.

        logger.decreaseIndent()
        logger.log()
      }
    } else {
      logger.log("""🔴 No accounts found. Please create a directory in $directoryAppStatistics and re-run this script.""")
    }
  }

  private suspend fun account(directory: File) {
    val accountName = directory.name
    logger.log("""📒️ Running $accountName""")
    logger.increaseIndent()

    // Validate apps.
    val appsFile = directory.resolve("apps.json")
    val appsJson = appsFile.takeIf { it.exists() }?.readText() ?: "[]"
    val apps = json.decodeFromString(ListSerializer(App.serializer()), appsJson)

    if (apps.isEmpty()) {
      logger.log("""🔴 $appsFile is missing or empty. Please configure your apps and re-run this script.""")
      logger.log("Here's an example:")
      logger.log(json.encodeToString(ListSerializer(App.serializer()), listOf(App.EXAMPLE)))
      return
    }

    // Validate Play Store file.
    val playStoreFile = directory.resolve("play-store.json")
    val hasAndroidApps = apps.count { it.androidPackageName?.isNotBlank() == true } > 0

    if (hasAndroidApps && !playStoreFile.exists()) {
      logger.log("""🔴 In order to get reviews from the Play Store we need a Service account file at $playStoreFile""")
      logger.log("Here are some instructions: https://github.com/Triple-T/gradle-play-publisher#service-account")
      return
    }

    // Validate notifiers.
    val reviewFormatter = ReviewFormatter(locale, timeZone)
    val slackConfigurationFile = directory.resolve("slack.json")
    val slackNotifierConfiguration = slackConfigurationFile.takeIf { it.exists() }
      ?.readText()
      ?.let { json.decodeFromString(JsonSlackNotifierConfiguration.serializer(), it) }

    val telegramBotConfigurationFile = directory.resolve("telegram_bot.json")
    val telegramBotNotifierConfiguration = telegramBotConfigurationFile.takeIf { it.exists() }
      ?.readText()
      ?.let { json.decodeFromString(JsonTelegramBotNotifierConfiguration.serializer(), it) }

    val notifiers = StoreReviewsNotifier(
      reviewFormatter = reviewFormatter,
      slackNotifierConfiguration = slackNotifierConfiguration,
      telegramBotNotifierConfiguration = telegramBotNotifierConfiguration
    )

    if (notifiers.isEmpty()) {
      logger.log("""🔴 No notifiers configured for $accountName. We support the following:""")
      logger.increaseIndent()

      val supportedNotifiers = mapOf(
        slackConfigurationFile to SlackNotifier(JsonSlackNotifierConfiguration.EXAMPLE),
        telegramBotConfigurationFile to TelegramBotNotifier(JsonTelegramBotNotifierConfiguration.EXAMPLE)
      )

      supportedNotifiers.forEach { (file, notifier) ->
        logger.log("""${notifier.emoji()} ${notifier.name()} - Configure via $file - example:""")
        logger.log(notifier.configuration.asString(json))
      }
      return
    }

    // Stores.
    val stores = listOfNotNull(
      playStoreFile.takeIf { it.exists() }?.let { PlayStore(it) },
      AppleStore()
    )

    process(
      directory = directory,
      apps = apps,
      stores = stores,
      notifiers = notifiers
    )
  }

  private suspend fun process(directory: File, apps: List<App>, stores: List<Store>, notifiers: StoreReviewsNotifier) {
    val outputDirectory = directory.resolve("store-reviews/")
    outputDirectory.mkdirs()

    apps.forEach { app ->
      stores.forEach { store ->
        val appOutput = outputDirectory.resolve("${app.name}/${store.name()}")
        appOutput.mkdirs()

        logger.log("""⭐ Querying ${store.name()} ${app.name} reviews""")
        logger.increaseIndent()

        val reviews = store.reviews(app)
          .sortedBy { it.updated }
          .mapNotNull { review ->
            val file = appOutput.resolve("${review.id}.json")

            if (!file.exists() || file.length() == 0L) {
              file.writeText(json.encodeToString(Review.serializer(), review))
              review
            } else {
              null
            }
          }

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
