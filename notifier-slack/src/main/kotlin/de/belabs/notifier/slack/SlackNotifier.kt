package de.belabs.notifier.slack

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

class SlackNotifier<Configuration : SlackNotifierConfiguration>(
  override val configuration: Configuration,
) : Notifier<Configuration, SlackNotifierPayload> {
  private val json = Json
  private val httpClient = HttpClient(OkHttp.create())

  override fun name() =
    "Slack"

  override fun emoji() =
    """📱"""

  override suspend fun notify(payload: SlackNotifierPayload) {
    httpClient.post(configuration.hook) {
      header(HttpHeaders.ContentType, ContentType.Application.Json)
      setBody(json.encodeToString(SlackNotifierPayload.serializer(), payload))
    }
  }
}

@Serializable data class SlackNotifierPayload(
  @SerialName("icon_emoji") val iconEmoji: String,
  @SerialName("username") val username: String,
  @SerialName("text") val text: String,
)

interface SlackNotifierConfiguration {
  val hook: String
}
