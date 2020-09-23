package de.belabs.appstatistics.storereviews

import de.belabs.appstatistics.storereviews.notifier.NotifierReviewFilter
import java.util.Locale

internal fun NotifierReviewFilter?.matches(review: Review) =
  this == null || languages.orEmpty().any { languageFilter ->
    review.language.orEmpty().toLowerCase(Locale.ROOT)
      .startsWith(languageFilter.toLowerCase(Locale.ROOT))
  }
