@file:Suppress("DEPRECATION")

package de.belabs.appstatistics.storereviews.store

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.services.androidpublisher.AndroidPublisher
import com.google.api.services.androidpublisher.AndroidPublisherScopes
import de.belabs.appstatistics.storereviews.App
import de.belabs.appstatistics.storereviews.Review
import java.io.File
import java.time.Instant

@Suppress("BlockingMethodInNonBlockingContext") internal class PlayStore(
  private val credentialsFile: File
) : Store {
  private val credentials = GoogleCredential.fromStream(credentialsFile.inputStream())
    .createScoped(listOf(AndroidPublisherScopes.ANDROIDPUBLISHER))

  private val androidPublisher = AndroidPublisher.Builder(GoogleNetHttpTransport.newTrustedTransport(), JacksonFactory.getDefaultInstance(), credentials)
    .setApplicationName("store-reviews")
    .build()

  override fun name() = "Android"

  override suspend fun reviews(app: App): List<Review> {
    if (app.androidPackageName != null) {
      return androidPublisher.reviews().list(app.androidPackageName).execute().reviews.orEmpty()
        .map {
          Review(
            id = it.reviewId,
            title = null,
            content = it.comments.first().userComment.text.trim(),
            rating = it.comments.first().userComment.starRating,
            version = it.comments.first().userComment.appVersionName,
            author = it.authorName ?: "?",
            updated = Instant.ofEpochSecond(it.comments.first().userComment.lastModified.seconds)
          )
        }
    }

    return emptyList()
  }
}
