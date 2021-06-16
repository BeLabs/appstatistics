package de.belabs.appstatistics.inappproducts

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable internal data class App(
  @SerialName("name") val name: String,
  @SerialName("android_package_name") val androidPackageName: String,
) {
  internal companion object {
    val EXAMPLE = App(
      name = "BeCoach",
      androidPackageName = "app.becoach.android.coachee",
    )
  }
}
