package de.belabs.appstatistics.storereviews.store

import de.belabs.appstatistics.storereviews.App
import de.belabs.appstatistics.storereviews.Review

internal interface Store {
  fun name(): String

  suspend fun reviews(app: App): List<Review>
}
