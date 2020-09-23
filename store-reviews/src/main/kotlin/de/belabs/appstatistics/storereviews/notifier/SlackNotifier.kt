package de.belabs.appstatistics.storereviews.notifier

import de.belabs.appstatistics.storereviews.App
import de.belabs.appstatistics.storereviews.Review
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.post
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UnstableDefault
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle.MEDIUM
import java.util.Locale

internal class SlackNotifier(
  private val configuration: SlackConfiguration,
  private val zoneId: ZoneId
) : Notifier {
  private val httpClient = HttpClient(OkHttp.create())

  @OptIn(UnstableDefault::class)
  private val json = Json(JsonConfiguration.Default)

  override fun name() =
    "Slack"

  override fun emoji() =
    """📱"""

  override suspend fun notify(locale: Locale, app: App, storeName: String, review: Review) {
    httpClient.post<Unit>(configuration.hook) {
      val stringBuilder = StringBuilder()
      stringBuilder.append("★".repeat(review.rating))
      stringBuilder.append("☆".repeat(5 - review.rating))
      stringBuilder.append("\n")

      if (review.title != null) stringBuilder.append(">*${review.title}*\n")
      stringBuilder.append(">${review.content}\n")

      val date = DateTimeFormatter.ofLocalizedDateTime(MEDIUM)
        .withLocale(locale)
        .format(LocalDateTime.ofInstant(review.updated, zoneId))

      stringBuilder.append(
        when (locale) {
          Locale.GERMAN, Locale.GERMANY -> "von *${review.author}* am _${date}_ mit Version _${review.version}_"
          else -> "by *${review.author}* on _${date}_ with version _${review.version}_"
        }
      )

      body = json.stringify(
        SlackPayload.serializer(),
        SlackPayload(
          iconEmoji = configuration.emoji ?: ":${app.name.toLowerCase(Locale.ROOT)}:",
          username = configuration.username ?: "${app.name} ($storeName)",
          text = stringBuilder.toString()
        )
      )
    }
  }
}

@Serializable private data class SlackPayload(
  @SerialName("icon_emoji") val iconEmoji: String,
  @SerialName("username") val username: String,
  @SerialName("text") val text: String
)

@Serializable internal data class SlackConfiguration(
  @SerialName("emoji") val emoji: String? = null,
  @SerialName("username") val username: String? = null,
  @SerialName("hook") val hook: String
) {
  companion object {
    val EXAMPLE = SlackConfiguration(
      emoji = ":star:",
      username = "Review",
      hook = "https://hooks.slack.com/services/ASDFDSR32/TW863FSGDGA/Ad344SDAHTYOJTGE2354DGSF"
    )
  }
}
