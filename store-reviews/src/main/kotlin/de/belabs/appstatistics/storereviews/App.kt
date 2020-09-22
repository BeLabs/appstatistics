package de.belabs.appstatistics.storereviews

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable internal data class App(
  @SerialName("name") val name: String,
  @SerialName("android_package_name") val androidPackageName: String?,
  @SerialName("apple_id") val appleId: String?,
  @SerialName("apple_number_of_pages_to_check") val appleNumberOfPagesToCheck: Int = 5,
  @SerialName("apple_languages_to_check") val appleLanguagesToCheck: List<String> = listOf("US", "DE")
) {
  internal companion object {
    val EXAMPLE = App(
      name = "BeCoach",
      androidPackageName = "app.becoach.android.coachee",
      appleId = "1489873599"
    )
  }
}
