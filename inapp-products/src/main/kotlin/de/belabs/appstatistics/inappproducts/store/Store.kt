package de.belabs.appstatistics.inappproducts.store

import de.belabs.appstatistics.inappproducts.App
import de.belabs.appstatistics.takeIfNotBlank
import java.io.File

data class StoreInAppProductListing(
  val title: String,
  val description: String,
)

private const val APPLE_MAX_TITLE_LENGTH = 30
private const val APPLE_MAX_DESCRIPTION_LENGTH = 55

data class StoreInAppProduct(
  val id: String,
  val prettyString: String,
  val listings: Map<String, StoreInAppProductListing>,
) {
  fun appleErrorsOrNull(): String? {
    val default = listings.getValue("en-US")

    val invalidTitleLocales = listings.filter { (_, listing) -> listing.title.length > 30 }.keys.joinToString().takeIfNotBlank()
    val invalidDescriptionLocales = listings.filter { (_, listing) -> listing.description.length > 55 }.keys.joinToString().takeIfNotBlank()

    return listOfNotNull(
      invalidTitleLocales?.let { "Title \"${default.title}\" is too long (max=$APPLE_MAX_TITLE_LENGTH) in $it" },
      invalidDescriptionLocales?.let { "Description \"${default.description}\" is too long (max=$APPLE_MAX_DESCRIPTION_LENGTH) in $it" },
    ).joinToString(separator = " & ").takeIfNotBlank()
  }
}

internal interface Store {
  fun name(): String

  suspend fun inAppProducts(app: App): List<StoreInAppProduct>
  suspend fun create(app: App, file: File): StoreInAppProduct
  suspend fun edit(app: App, file: File): StoreInAppProduct
  suspend fun edit(app: App, inAppProduct: StoreInAppProduct): StoreInAppProduct
}
