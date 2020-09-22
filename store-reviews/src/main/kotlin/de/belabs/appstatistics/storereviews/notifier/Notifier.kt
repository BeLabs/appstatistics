package de.belabs.appstatistics.storereviews.notifier

import de.belabs.appstatistics.storereviews.App
import de.belabs.appstatistics.storereviews.Review
import java.util.Locale

internal interface Notifier {
  fun name(): String

  fun emoji(): String

  suspend fun notify(locale: Locale, app: App, storeName: String, review: Review)
}
