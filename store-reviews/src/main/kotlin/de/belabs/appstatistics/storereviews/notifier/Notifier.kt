package de.belabs.appstatistics.storereviews.notifier

import de.belabs.appstatistics.storereviews.App
import de.belabs.appstatistics.storereviews.Review
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

internal interface Notifier {
  fun name(): String

  fun emoji(): String

  fun configuration(): NotifierConfiguration

  suspend fun notify(app: App, storeName: String, review: Review)
}

internal interface NotifierConfiguration {
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
