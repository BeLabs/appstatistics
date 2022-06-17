package de.belabs.appstatistics.storereviews

import org.junit.Test
import kotlin.test.assertEquals

class ExtensionTest {
  @Test fun notifierReviewFilterNullMatches() {
    assertEquals(
      expected = true,
      actual = null.matches(review1Star),
    )
  }

  @Test fun notifierReviewFilterEmptyNoMatch() {
    assertEquals(
      expected = false,
      actual = NotifierReviewFilter().matches(review1Star.copy(language = "de")),
    )
  }

  @Test fun notifierReviewFilterMatch() {
    assertEquals(
      expected = true,
      actual = NotifierReviewFilter(languages = listOf("de")).matches(review1Star.copy(language = "de")),
    )
  }

  @Test fun notifierReviewFilterCaseInsensitive() {
    assertEquals(
      expected = true,
      actual = NotifierReviewFilter(languages = listOf("de")).matches(review1Star.copy(language = "DE")),
    )
  }

  @Test fun notifierReviewFilterCaseStartsWith() {
    assertEquals(
      expected = true,
      actual = NotifierReviewFilter(languages = listOf("de")).matches(review1Star.copy(language = "de-DE")),
    )
  }
}
