package de.belabs.appstatistics.storereviews

import com.vanniktech.locale.Language
import com.vanniktech.locale.Locale
import com.vanniktech.locale.toJavaLocale
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle.MEDIUM

internal class ReviewFormatter(
  private val language: Language,
  private val zoneId: ZoneId,
) {
  fun asText(appName: String, storeName: String, review: Review): String {
    val stringBuilder = StringBuilder()
    stringBuilder.append("${review.ratingLine()} - ${review.author} - $appName ($storeName)")

    if (review.version != null) {
      stringBuilder.append(" v${review.version}")
    }

    stringBuilder.append(" - ${review.date()}\n\n")

    if (review.title != null) {
      stringBuilder.append(review.title).append("\n\n")
    }

    stringBuilder.append(review.content)
    return stringBuilder.toString()
  }

  fun asMarkdown(review: Review): String {
    val stringBuilder = StringBuilder()
    stringBuilder.append(review.ratingLine()).append("\n")

    if (review.title != null) {
      stringBuilder.append(">*${review.title}*\n")
    }

    stringBuilder.append(">${review.content}\n")

    stringBuilder.append(
      when (language) {
        Language.GERMAN -> "von *${review.author}* am _${review.date()}_"
        else -> "by *${review.author}* on _${review.date()}_"
      },
    )

    if (review.version != null) {
      stringBuilder.append(
        when (language) {
          Language.GERMAN -> " mit Version _${review.version}_"
          else -> " with version _${review.version}_"
        },
      )
    }

    return stringBuilder.toString()
  }

  private fun Review.ratingLine() = "★".repeat(rating)
    .plus("☆".repeat(5 - rating))

  private fun Review.date() = DateTimeFormatter.ofLocalizedDateTime(MEDIUM)
    .withLocale(Locale(this@ReviewFormatter.language, null).toJavaLocale())
    .format(LocalDateTime.ofInstant(updated, zoneId))
}
