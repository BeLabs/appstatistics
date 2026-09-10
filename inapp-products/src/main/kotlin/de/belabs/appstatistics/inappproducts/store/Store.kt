package de.belabs.appstatistics.inappproducts.store

import de.belabs.appstatistics.inappproducts.App
import java.io.File

data class StoreInAppProductListing(
  val title: String,
  val description: String,
)

data class StoreInAppProduct(
  val sku: String,
  val prettyString: String,
  val listings: Map<String, StoreInAppProductListing>,
)

internal interface Store {
  fun name(): String

  suspend fun inAppProducts(app: App): List<StoreInAppProduct>
  suspend fun create(app: App, file: File): StoreInAppProduct
  suspend fun edit(app: App, file: File): StoreInAppProduct
  suspend fun edit(app: App, inAppProduct: StoreInAppProduct): StoreInAppProduct
}
