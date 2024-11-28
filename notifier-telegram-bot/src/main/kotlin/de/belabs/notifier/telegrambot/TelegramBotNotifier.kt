package de.belabs.notifier.telegrambot

import de.belabs.notifier.Notifier
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class TelegramBotNotifier<Configuration : TelegramBotNotifierConfiguration>(
  override val configuration: Configuration,
) : Notifier<Configuration, TelegramBotNotifierPayload> {
  private val json = Json
  private val httpClient = HttpClient(OkHttp.create())

  override fun name() = "Telegram Bot"

  override fun emoji() = """✈️ """

  override suspend fun notify(payload: TelegramBotNotifierPayload) {
    httpClient.post("https://api.telegram.org/bot${configuration.botToken}/sendMessage") {
      header(HttpHeaders.ContentType, ContentType.Application.Json)
      setBody(json.encodeToString(TelegramBotNotifierPayload.serializer(), payload))
    }
  }
}

@Serializable data class TelegramBotNotifierPayload(
  @SerialName("chat_id") val chatId: Long,
  @SerialName("text") val text: String,
)

interface TelegramBotNotifierConfiguration {
  val botToken: String
}
