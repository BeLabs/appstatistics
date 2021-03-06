package de.belabs.appstatistics.storereviews

import org.junit.Test
import java.time.ZoneId
import java.util.Locale
import kotlin.test.assertEquals

class ReviewFormatterTest {
  private val zoneId = ZoneId.of("Europe/Berlin")

  @Test fun text1StarNoVersion() {
    val reviewFormatter = ReviewFormatter(Locale.GERMAN, zoneId)

    assertEquals(
      expected = """
        ★☆☆☆☆ - Me - Android - 23.09.2020 08:54:39

        Really nice app. 1 stars
        """.trimIndent(),
      actual = reviewFormatter.asText("Android", review1Star.copy(version = null))
    )
  }

  @Test fun text5Stars() {
    val reviewFormatter = ReviewFormatter(Locale.ENGLISH, zoneId)

    assertEquals(
      expected = """
        ★★★★★ - Me - iOS v1.0.0 - Sep 23, 2020 8:54:39 AM

        5 Stars title

        Really nice app. 5 stars
        """.trimIndent(),
      actual = reviewFormatter.asText("iOS", review5Stars)
    )
  }

  @Test fun markdown1StarVersionNull() {
    val reviewFormatter = ReviewFormatter(Locale.GERMAN, zoneId)

    assertEquals(
      expected = """
        ★☆☆☆☆
        >Really nice app. 1 stars
        von *Me* am _23.09.2020 08:54:39_
        """.trimIndent(),
      actual = reviewFormatter.asMarkdown(review1Star.copy(version = null))
    )
  }

  @Test fun markdown5Stars() {
    val reviewFormatter = ReviewFormatter(Locale.ENGLISH, zoneId)

    assertEquals(
      expected = """
        ★★★★★
        >*5 Stars title*
        >Really nice app. 5 stars
        by *Me* on _Sep 23, 2020 8:54:39 AM_ with version _1.0.0_
        """.trimIndent(),
      actual = reviewFormatter.asMarkdown(review5Stars)
    )
  }
}
