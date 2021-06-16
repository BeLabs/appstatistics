package de.belabs.appstatistics.inappproducts.store

import com.google.api.services.androidpublisher.model.InAppProduct
import de.belabs.appstatistics.inappproducts.App

internal interface Store {
  fun name(): String

  suspend fun inAppProducts(app: App): List<InAppProduct>
}
