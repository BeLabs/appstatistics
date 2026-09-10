package de.belabs.appstatistics.inappproducts.store

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.androidpublisher.AndroidPublisher
import com.google.api.services.androidpublisher.AndroidPublisherScopes
import com.google.api.services.androidpublisher.model.InAppProduct
import com.google.api.services.androidpublisher.model.OneTimeProduct
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
    try {
      val products = androidPublisher.inappproducts()
        .list(app.androidPackageName)
        .execute()

      return products?.inappproduct
        ?.sortedBy { it.sku }
        .orEmpty()
        .map { it.converted() }
    } catch (exception: GoogleJsonResponseException) {
      if (exception.statusCode == 403 && exception.statusMessage == "Forbidden") {
        val all = mutableListOf<OneTimeProduct>()
        var pageToken: String? = null

        do {
          val response = androidPublisher.monetization()
            .onetimeproducts()
            .list(app.androidPackageName)
            .setPageSize(1000)
            .setPageToken(pageToken)
            .execute()

          all += response.oneTimeProducts.orEmpty()
          pageToken = response.nextPageToken
        } while (!pageToken.isNullOrEmpty())

        return all.map { it.converted() }
      } else {
        throw exception
      }
    }
  }

  override suspend fun create(
    app: App,
    file: File,
  ): StoreInAppProduct {
    val inAppProduct = inAppProduct(file, app)

    return androidPublisher.inappproducts()
      .insert(inAppProduct.packageName, inAppProduct)
      .execute()
      .converted()
  }

  override suspend fun edit(app: App, file: File): StoreInAppProduct {
    val inAppProduct = inAppProduct(file, app)

    return androidPublisher.inappproducts()
      .update(inAppProduct.packageName, inAppProduct.sku, inAppProduct)
      .execute()
      .converted()
  }

  override suspend fun edit(app: App, inAppProduct: StoreInAppProduct): StoreInAppProduct {
    val current = androidPublisher.inappproducts().get(app.androidPackageName, inAppProduct.sku)
      .execute()

    require(current.packageName == app.androidPackageName) {
      "Package names differ. Expected \"${app.androidPackageName}\" Actual: \"${current.packageName}\""
    }

    current.listings.forEach { (key, value) ->
      val match = inAppProduct.listings.getValue(key)
      value.title = match.title
      value.description = match.description
    }

    return androidPublisher.inappproducts()
      .update(current.packageName, current.sku, current)
      .execute()
      .converted()
  }

  private fun inAppProduct(
    file: File,
    app: App,
  ): InAppProduct {
    val inAppProduct = GsonFactory.getDefaultInstance()
      .fromString(file.readText(), InAppProduct::class.java)

    require(inAppProduct.packageName == app.androidPackageName) {
      "Package names differ. Expected \"${app.androidPackageName}\" Actual: \"${inAppProduct.packageName}\" in $file"
    }

    require(file.nameWithoutExtension == inAppProduct.sku) {
      "Sku's differ. Expected \"${file.nameWithoutExtension}\" Actual: \"${inAppProduct.sku}\" in $file"
    }

    return inAppProduct
  }
}

private fun InAppProduct.converted() = StoreInAppProduct(
  sku = sku,
  prettyString = toPrettyString(),
  listings = listings.mapValues {
    StoreInAppProductListing(
      title = it.value.title.trim(),
      description = it.value.description.trim(),
    )
  },
)

private fun OneTimeProduct.converted() = StoreInAppProduct(
  sku = productId,
  prettyString = toPrettyString(),
  listings = listings.associate {
    it.languageCode to StoreInAppProductListing(
      it.title,
      it.description
    )
  },
)
