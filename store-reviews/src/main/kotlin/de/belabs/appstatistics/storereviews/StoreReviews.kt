package de.belabs.appstatistics.storereviews

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import de.belabs.appstatistics.storereviews.notifier.Notifier
import de.belabs.appstatistics.storereviews.notifier.SlackNotifier
import de.belabs.appstatistics.storereviews.store.AppleStore
import de.belabs.appstatistics.storereviews.store.PlayStore
import de.belabs.appstatistics.storereviews.store.Store
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlinx.serialization.UnstableDefault
import kotlinx.serialization.builtins.list
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration
import java.io.File
import java.time.ZoneId
import java.util.Locale

@OptIn(UnstableDefault::class) internal class StoreReviews : CliktCommand() {
  private val directoryAppStatistics: File by option(help = "App statistics directory")
    .convert { File(it) }
    .default(File(System.getProperty("user.home")).resolve(".appstatistics"))

  private val locale: Locale by option(help = "Locale")
    .convert { Locale(it) }
    .default(Locale.getDefault())

  private val timeZone: ZoneId by option(help = "Time zone")
    .convert { ZoneId.of(it) }
    .default(ZoneId.systemDefault())

  private val json = Json(JsonConfiguration.Default.copy(prettyPrint = true))

  private val logger = Logger()

  override fun run() = runBlocking {
    logger.log("""🔎 Scanning for accounts in $directoryAppStatistics""")
    logger.increaseIndent()

    val organizations = directoryAppStatistics.listFiles { current, name -> current.resolve(name).isDirectory }.orEmpty()

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
    val json = appsFile.takeIf { it.exists() }?.readText() ?: "[]"
    val apps = this.json.parse(App.serializer().list, json)

    if (apps.isEmpty()) {
      logger.log("""🔴 $appsFile is missing or empty. Please configure your apps and re-run this script.""")
      logger.log("Here's an example:")
      logger.log(this.json.stringify(App.serializer().list, listOf(App.EXAMPLE)))
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

    // Validate Slack.
    val slackHookFile = directory.resolve("slack_hook")
    val slackHook = slackHookFile.takeIf { it.exists() }?.readText()
    val notifiers = listOfNotNull(
      slackHook?.let { SlackNotifier(it, timeZone) }
    )

    if (notifiers.isEmpty()) {
      logger.log("""🔴 No notifiers configured for $accountName. We support:""")
      logger.increaseIndent()
      logger.log("""📱 Slack - create a file at $slackHookFile containing the fully qualified WebHook url""")
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

  private suspend fun process(directory: File, apps: List<App>, stores: List<Store>, notifiers: List<Notifier>) {
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
              file.writeText(json.stringify(Review.serializer(), review))
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

          notifiers.forEach { notifier ->
            logger.log("""${notifier.emoji()} Posting reviews to ${notifier.name()}""")
            reviews.forEach { review ->
              notifier.notify(locale, app, store.name(), review)
            }
          }

          logger.decreaseIndent()
        }

        logger.log()
        logger.decreaseIndent()
      }
    }
  }
}
