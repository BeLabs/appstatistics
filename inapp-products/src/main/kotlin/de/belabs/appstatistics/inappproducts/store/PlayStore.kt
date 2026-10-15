package de.belabs.appstatistics.inappproducts.store

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.androidpublisher.AndroidPublisher
import com.google.api.services.androidpublisher.AndroidPublisherScopes
import com.google.api.services.androidpublisher.model.OneTimeProduct
import com.google.api.services.androidpublisher.model.OneTimeProductListing
import com.google.auth.http.HttpCredentialsAdapter
import com.google.auth.oauth2.GoogleCredentials
import de.belabs.appstatistics.inappproducts.App
import java.io.File

internal class PlayStore(
  credentialsFile: File,
) : Store {
  private val credentials = GoogleCredentials.fromStream(credentialsFile.inputStream())
    .createScoped(listOf(AndroidPublisherScopes.ANDROIDPUBLISHER))

  private val androidPublisher = AndroidPublisher.Builder(GoogleNetHttpTransport.newTrustedTransport(), GsonFactory.getDefaultInstance(), HttpCredentialsAdapter(credentials))
    .setApplicationName("inapp-products")
    .build()

  override fun name() = "Android"

  override suspend fun inAppProducts(app: App): List<StoreInAppProduct> {
    val oneTimeProducts = mutableListOf<OneTimeProduct>()
    var pageToken: String? = null

    runCatching {
      do {
        val response = androidPublisher.monetization()
          .onetimeproducts()
          .list(app.androidPackageName)
          .setPageSize(1000)
          .setPageToken(pageToken)
          .execute()

        oneTimeProducts += response.oneTimeProducts.orEmpty()
        pageToken = response.nextPageToken
      } while (!pageToken.isNullOrEmpty())
    }

    return oneTimeProducts
      .map { it.converted() }
  }

  override suspend fun create(
    app: App,
    file: File,
  ): StoreInAppProduct {
    val oneTimeProduct = inAppProduct(file, app)

    return androidPublisher.monetization().onetimeproducts()
      .patch(oneTimeProduct.packageName, oneTimeProduct.productId, oneTimeProduct)
      .setAllowMissing(true)
      .setUpdateMask("listings,purchaseOptions")
      .setRegionsVersionVersion(oneTimeProduct.regionsVersion.version)
      .execute()
      .converted()
  }

  override suspend fun edit(app: App, file: File): StoreInAppProduct {
    val oneTimeProduct = inAppProduct(file, app)

    return androidPublisher.monetization().onetimeproducts()
      .patch(oneTimeProduct.packageName, oneTimeProduct.productId, oneTimeProduct)
      .setAllowMissing(false)
      .setUpdateMask("listings,purchaseOptions")
      .setRegionsVersionVersion(oneTimeProduct.regionsVersion.version)
      .execute()
      .converted()
  }

  override suspend fun edit(app: App, inAppProduct: StoreInAppProduct): StoreInAppProduct {
    val current = androidPublisher.monetization().onetimeproducts().get(app.androidPackageName, inAppProduct.id)
      .execute()

    require(current.packageName == app.androidPackageName) {
      "Package names differ. Expected \"${app.androidPackageName}\" Actual: \"${current.packageName}\""
    }

    current.listings = inAppProduct.listings.map { (languageCode, listing) ->
      OneTimeProductListing()
        .setLanguageCode(languageCode)
        .setTitle(listing.title)
        .setDescription(listing.description)
    }

    return androidPublisher.monetization().onetimeproducts()
      .patch(app.androidPackageName, inAppProduct.id, current)
      .setUpdateMask("listings")
      .setRegionsVersionVersion(current.regionsVersion.version)
      .execute()
      .converted()
  }

  private fun inAppProduct(
    file: File,
    app: App,
  ): OneTimeProduct {
    val inAppProduct = GsonFactory.getDefaultInstance()
      .fromString(file.readText(), OneTimeProduct::class.java)

    require(inAppProduct.packageName == app.androidPackageName) {
      "Package names differ. Expected \"${app.androidPackageName}\" Actual: \"${inAppProduct.packageName}\" in $file"
    }

    require(file.nameWithoutExtension == inAppProduct.productId) {
      "Product id differ. Expected \"${file.nameWithoutExtension}\" Actual: \"${inAppProduct.productId}\" in $file"
    }

    return inAppProduct
  }
}

private fun OneTimeProduct.converted() = StoreInAppProduct(
  id = productId,
  prettyString = toPrettyString(),
  listings = listings.associate {
    it.languageCode to StoreInAppProductListing(
      it.title,
      it.description,
    )
  },
)
