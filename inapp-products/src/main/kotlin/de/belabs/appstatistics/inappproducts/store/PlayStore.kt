@file:Suppress("DEPRECATION")

package de.belabs.appstatistics.inappproducts.store

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.services.androidpublisher.AndroidPublisher
import com.google.api.services.androidpublisher.AndroidPublisherScopes
import com.google.api.services.androidpublisher.model.InAppProduct
import de.belabs.appstatistics.inappproducts.App
import java.io.File

@Suppress("BlockingMethodInNonBlockingContext") internal class PlayStore(
  private val credentialsFile: File
) : Store {
  private val credentials = GoogleCredential.fromStream(credentialsFile.inputStream())
    .createScoped(listOf(AndroidPublisherScopes.ANDROIDPUBLISHER))

  private val androidPublisher = AndroidPublisher.Builder(GoogleNetHttpTransport.newTrustedTransport(), JacksonFactory.getDefaultInstance(), credentials)
    .setApplicationName("inapp-products")
    .build()

  override fun name() = "Android"

  override suspend fun inAppProducts(app: App): List<InAppProduct> {
    val products = androidPublisher.inappproducts()
      .list(app.androidPackageName)
      .execute()

    return products.inappproduct
      .sortedBy { it.sku }
  }
}
