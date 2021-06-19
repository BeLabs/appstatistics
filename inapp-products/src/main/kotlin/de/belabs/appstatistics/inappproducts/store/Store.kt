package de.belabs.appstatistics.inappproducts.store

import com.google.api.services.androidpublisher.model.InAppProduct
import de.belabs.appstatistics.inappproducts.App
import java.io.File

internal interface Store {
  fun name(): String

  suspend fun inAppProducts(app: App): List<InAppProduct>
  suspend fun create(app: App, inappProducts: File): InAppProduct
}
