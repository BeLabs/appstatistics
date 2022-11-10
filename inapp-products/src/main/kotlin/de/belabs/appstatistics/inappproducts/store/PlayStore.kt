@file:Suppress("DEPRECATION")

package de.belabs.appstatistics.inappproducts.store

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.androidpublisher.AndroidPublisher
import com.google.api.services.androidpublisher.AndroidPublisherScopes
import com.google.api.services.androidpublisher.model.InAppProduct
import de.belabs.appstatistics.inappproducts.App
import java.io.File

@Suppress("BlockingMethodInNonBlockingContext") internal class PlayStore(
  private val credentialsFile: File,
) : Store {
  private val credentials = GoogleCredential.fromStream(credentialsFile.inputStream())
    .createScoped(listOf(AndroidPublisherScopes.ANDROIDPUBLISHER))

  private val androidPublisher = AndroidPublisher.Builder(GoogleNetHttpTransport.newTrustedTransport(), GsonFactory.getDefaultInstance(), credentials)
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

    return try {
      androidPublisher.inappproducts()
        .update(inAppProduct.packageName, inAppProduct.sku, inAppProduct)
        .execute()
    } catch (throwable: GoogleJsonResponseException) {
      if (throwable.details.message == "Cannot update price - product has pricing template.") {
        // https://issuetracker.google.com/issues/258484265
        inAppProduct
      } else {
        throw throwable
      }
    }
  }

  override suspend fun edit(app: App, inAppProduct: InAppProduct): InAppProduct {
    require(inAppProduct.packageName == app.androidPackageName) {
      "Package names differ. Expected \"${app.androidPackageName}\" Actual: \"${inAppProduct.packageName}\""
    }

    return try {
      androidPublisher.inappproducts()
        .update(inAppProduct.packageName, inAppProduct.sku, inAppProduct)
        .execute()
    } catch (throwable: GoogleJsonResponseException) {
      if (throwable.details.message == "Cannot update price - product has pricing template.") {
        // https://issuetracker.google.com/issues/258484265
        inAppProduct
      } else {
        throw throwable
      }
    }
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
