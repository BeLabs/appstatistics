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
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle.MEDIUM
import java.util.Locale

internal class TelegramBotNotifier(
  private val configuration: TelegramBotNotifierConfiguration,
  private val reviewFormatter: ReviewFormatter
) : Notifier {
  private val httpClient = HttpClient(OkHttp.create())

  @OptIn(UnstableDefault::class)
  private val json = Json(JsonConfiguration.Default)

  override fun name() =
    "Telegram Bot"

  override fun emoji() =
    """✈️ """

  override fun configuration() = configuration

  override suspend fun notify(app: App, storeName: String, review: Review) {
    httpClient.post<Unit>("https://api.telegram.org/bot${configuration.botToken}/sendMessage") {
      header(HttpHeaders.ContentType, ContentType.Application.Json)

      body = json.stringify(
        TelegramBotPayload.serializer(),
        TelegramBotPayload(
          chatId = configuration.chatId,
          text = reviewFormatter.asText(storeName, review)
        )
      )
    }
  }
}

@Serializable private data class TelegramBotPayload(
  @SerialName("chat_id") val chatId: Long,
  @SerialName("text") val text: String
)

@Serializable internal data class TelegramBotNotifierConfiguration(
  @SerialName("bot_token") val botToken: String,
  @SerialName("chat_id") val chatId: Long
) : NotifierConfiguration {
  override fun asString(json: Json) = json.stringify(serializer(), this)

  companion object {
    val EXAMPLE = TelegramBotNotifierConfiguration(
      chatId = 3431432432,
      botToken = "7205431853:up1jf5adDSF5qewfaUi8r564rgDFsfasdaA"
    )
  }
}
