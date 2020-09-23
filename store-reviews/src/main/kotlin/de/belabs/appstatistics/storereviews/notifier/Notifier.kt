package de.belabs.appstatistics.storereviews.notifier

import de.belabs.appstatistics.storereviews.App
import de.belabs.appstatistics.storereviews.Review
import kotlinx.serialization.json.Json

internal interface Notifier {
  fun name(): String

  fun emoji(): String

  fun configuration(): NotifierConfiguration

  suspend fun notify(app: App, storeName: String, review: Review)
}

internal interface NotifierConfiguration {
  fun asString(json: Json): String
}
