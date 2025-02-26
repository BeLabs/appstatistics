package de.belabs.appstatistics.storereviews.store

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.androidpublisher.AndroidPublisher
import com.google.api.services.androidpublisher.AndroidPublisherScopes
import com.google.auth.http.HttpCredentialsAdapter
import com.google.auth.oauth2.GoogleCredentials
import de.belabs.appstatistics.storereviews.App
import de.belabs.appstatistics.storereviews.Review
import kotlinx.datetime.Instant
import java.io.File

internal class PlayStore(
  credentialsFile: File,
) : Store {
  private val credentials = GoogleCredentials.fromStream(credentialsFile.inputStream())
    .createScoped(listOf(AndroidPublisherScopes.ANDROIDPUBLISHER))

  private val androidPublisher = AndroidPublisher.Builder(GoogleNetHttpTransport.newTrustedTransport(), GsonFactory.getDefaultInstance(), HttpCredentialsAdapter(credentials))
    .setApplicationName("store-reviews")
    .build()

  override fun name() = "Android"

  override suspend fun reviews(app: App): List<Review> {
    if (app.androidPackageName != null) {
      return androidPublisher.reviews().list(app.androidPackageName).execute().reviews.orEmpty()
        .map {
          val comment = it.comments.first()

          Review(
            id = it.reviewId,
            title = null,
            content = comment.userComment.text.trim(),
            rating = comment.userComment.starRating,
            version = comment.userComment.appVersionName,
            language = comment.userComment.reviewerLanguage,
            author = it.authorName ?: "?",
            updated = Instant.fromEpochSeconds(comment.userComment.lastModified.seconds),
          )
        }
    }

    return emptyList()
  }
}
