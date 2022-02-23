package de.belabs.appstatistics.storereviews

import de.belabs.appstatistics.Logger
import de.belabs.notifier.slack.SlackNotifier
import de.belabs.notifier.slack.SlackNotifierConfiguration
import de.belabs.notifier.slack.SlackNotifierPayload
import de.belabs.notifier.telegrambot.TelegramBotNotifier
import de.belabs.notifier.telegrambot.TelegramBotNotifierConfiguration
import de.belabs.notifier.telegrambot.TelegramBotNotifierPayload
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.Locale

internal class StoreReviewsNotifier(
  private val directory: File,
  private val reviewFormatter: ReviewFormatter,
  private val slackNotifierConfiguration: JsonSlackNotifierConfiguration?,
  private val telegramBotNotifierConfiguration: JsonTelegramBotNotifierConfiguration?
) {
  init {
    directory.mkdirs()
  }

  fun isEmpty() = slackNotifierConfiguration == null && telegramBotNotifierConfiguration == null

  fun missing(reviewIds: List<String>): List<String> {
    return reviewIds.filter { reviewId ->
      (slackNotifierConfiguration != null && !directory.resolve(".$reviewId-slack").exists())
        || (telegramBotNotifierConfiguration != null && !directory.resolve(".$reviewId-telegram").exists())
    }
  }

  fun canNotify(review: Review): Boolean {
    return (slackNotifierConfiguration != null && slackNotifierConfiguration.reviewFilter.matches(review))
      || (telegramBotNotifierConfiguration != null && telegramBotNotifierConfiguration.reviewFilter.matches(review))
  }

  suspend fun notify(logger: Logger, app: App, storeName: String, reviews: List<Review>) {
    notifySlack(logger, app, storeName, reviews)
    notifyTelegram(logger, storeName, reviews)
  }

  private suspend fun notifySlack(logger: Logger, app: App, storeName: String, reviews: List<Review>) {
    slackNotifierConfiguration?.let {
      val notifier = SlackNotifier(it)
      val filteredReviews = reviews.filter { review -> it.reviewFilter.matches(review) && !directory.resolve(".slack-${review.id}").exists() }

      logger.log("""${notifier.emoji()} Posting ${filteredReviews.size} reviews to ${notifier.name()}""")

      filteredReviews.forEach { review ->
        try {
          notifier.notify(SlackNotifierPayload(
            iconEmoji = notifier.configuration.emoji ?: ":${app.name.lowercase()}:",
            username = notifier.configuration.username ?: "${app.name} ($storeName)",
            text = reviewFormatter.asMarkdown(review)
          ))

          directory.resolve(".${review.id}-slack").writeText("")
        } catch (throwable: Throwable) {
          throw throwable
        }
      }
    }
  }

  private suspend fun notifyTelegram(logger: Logger, storeName: String, reviews: List<Review>) {
    telegramBotNotifierConfiguration?.let {
      val notifier = TelegramBotNotifier(it)
      val filteredReviews = reviews.filter { review -> it.reviewFilter.matches(review) && !directory.resolve(".telegram-${review.id}").exists() }

      logger.log("""${notifier.emoji()} Posting ${filteredReviews.size} reviews to ${notifier.name()}""")

      filteredReviews.forEach { review ->
        try {
          notifier.notify(TelegramBotNotifierPayload(
            chatId = notifier.configuration.chatId,
            text = reviewFormatter.asText(storeName, review)
          ))

          directory.resolve(".${review.id}-telegram").writeText("")
        } catch (throwable: Throwable) {
          throw throwable
        }
      }
    }
  }
}

internal interface StoreReviewsNotifierConfiguration {
  val reviewFilter: NotifierReviewFilter?

  fun asString(json: Json): String
}

@Serializable internal data class NotifierReviewFilter(
  @SerialName("languages") val languages: List<String>? = null
) {
  companion object {
    val EXAMPLE = NotifierReviewFilter(
      languages = listOf("de", "en")
    )
  }
}

@Serializable internal data class JsonSlackNotifierConfiguration(
  @SerialName("emoji") val emoji: String? = null,
  @SerialName("username") val username: String? = null,
  @SerialName("hook") override val hook: String,
  @SerialName("review_filter") override val reviewFilter: NotifierReviewFilter? = null
) : SlackNotifierConfiguration, StoreReviewsNotifierConfiguration {
  override fun asString(json: Json) = json.encodeToString(serializer(), this)

  companion object {
    val EXAMPLE = JsonSlackNotifierConfiguration(
      emoji = ":star:",
      username = "Review",
      hook = "https://hooks.slack.com/services/ASDFDSR32/TW863FSGDGA/Ad344SDAHTYOJTGE2354DGSF",
      reviewFilter = NotifierReviewFilter.EXAMPLE
    )
  }
}

@Serializable internal data class JsonTelegramBotNotifierConfiguration(
  @SerialName("bot_token") override val botToken: String,
  @SerialName("chat_id") val chatId: Long,
  @SerialName("review_filter") override val reviewFilter: NotifierReviewFilter? = null
) : TelegramBotNotifierConfiguration, StoreReviewsNotifierConfiguration {
  override fun asString(json: Json) = json.encodeToString(serializer(), this)

  companion object {
    val EXAMPLE = JsonTelegramBotNotifierConfiguration(
      chatId = 3431432432,
      botToken = "7205431853:up1jf5adDSF5qewfaUi8r564rgDFsfasdaA",
      reviewFilter = NotifierReviewFilter.EXAMPLE
    )
  }
}
