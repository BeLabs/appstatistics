package de.belabs.appstatistics.storereviews.notifier

import de.belabs.appstatistics.storereviews.App
import de.belabs.appstatistics.storereviews.Review
import de.belabs.appstatistics.storereviews.ReviewFormatter
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UnstableDefault
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration
import java.util.Locale

internal class SlackNotifier(
  private val configuration: SlackNotifierConfiguration,
  private val reviewFormatter: ReviewFormatter
) : Notifier {
  private val httpClient = HttpClient(OkHttp.create())

  @OptIn(UnstableDefault::class)
  private val json = Json(JsonConfiguration.Default)

  override fun name() =
    "Slack"

  override fun emoji() =
    """📱"""

  override fun configuration() = configuration

  override suspend fun notify(app: App, storeName: String, review: Review) {
    httpClient.post<Unit>(configuration.hook) {
      header(HttpHeaders.ContentType, ContentType.Application.Json)

      body = json.stringify(
        SlackPayload.serializer(),
        SlackPayload(
          iconEmoji = configuration.emoji ?: ":${app.name.toLowerCase(Locale.ROOT)}:",
          username = configuration.username ?: "${app.name} ($storeName)",
          text = reviewFormatter.asMarkdown(review)
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

@Serializable internal data class SlackNotifierConfiguration(
  @SerialName("emoji") val emoji: String? = null,
  @SerialName("username") val username: String? = null,
  @SerialName("hook") val hook: String,
  @SerialName("review_filter") override val reviewFilter: NotifierReviewFilter? = null
) : NotifierConfiguration {
  override fun asString(json: Json) = json.stringify(serializer(), this)

  companion object {
    val EXAMPLE = SlackNotifierConfiguration(
      emoji = ":star:",
      username = "Review",
      hook = "https://hooks.slack.com/services/ASDFDSR32/TW863FSGDGA/Ad344SDAHTYOJTGE2354DGSF",
      reviewFilter = NotifierReviewFilter.EXAMPLE
    )
  }
}
