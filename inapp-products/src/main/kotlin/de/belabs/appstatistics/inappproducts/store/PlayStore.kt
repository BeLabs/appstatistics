package de.belabs.appstatistics.inappproducts.store

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.androidpublisher.AndroidPublisher
import com.google.api.services.androidpublisher.AndroidPublisherScopes
import com.google.api.services.androidpublisher.model.InAppProduct
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

  override suspend fun inAppProducts(app: App): List<InAppProduct> {
    val products = androidPublisher.inappproducts()
      .list(app.androidPackageName)
      .execute()

    return products?.inappproduct
      ?.sortedBy { it.sku }
      .orEmpty()
  }

  override suspend fun create(
    app: App,
    file: File,
  ): InAppProduct {
    val inAppProduct = inAppProduct(file, app)

    return androidPublisher.inappproducts()
      .insert(inAppProduct.packageName, inAppProduct)
      .execute()
  }

  override suspend fun edit(app: App, file: File): InAppProduct {
    val inAppProduct = inAppProduct(file, app)

    return androidPublisher.inappproducts()
      .update(inAppProduct.packageName, inAppProduct.sku, inAppProduct)
      .execute()
  }

  override suspend fun edit(app: App, inAppProduct: InAppProduct): InAppProduct {
    require(inAppProduct.packageName == app.androidPackageName) {
      "Package names differ. Expected \"${app.androidPackageName}\" Actual: \"${inAppProduct.packageName}\""
    }

    return androidPublisher.inappproducts()
      .update(inAppProduct.packageName, inAppProduct.sku, inAppProduct)
      .execute()
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
