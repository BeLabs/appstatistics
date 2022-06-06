package de.belabs.appstatistics.inappproducts

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable internal data class App(
  @SerialName("name") val name: String,
  @SerialName("android_package_name") val androidPackageName: String,
  @SerialName("indentation") val indentation: String = "  ",
  @SerialName("android_resource_directory") val androidResourceDirectory: String? = null,
  @SerialName("android_resource_strings_file_name") val androidResourceStringsFileName: String = "strings.xml",
) {
  internal companion object {
    val EXAMPLE = App(
      name = "BeCoach",
      androidPackageName = "app.becoach.android.coachee",
      androidResourceDirectory = "/Users/me/dev/BeCoach/app/src/main/res/",
    )
  }
}
