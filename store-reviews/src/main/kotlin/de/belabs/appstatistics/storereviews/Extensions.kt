package de.belabs.appstatistics.storereviews

internal fun NotifierReviewFilter?.matches(review: Review) = this == null || languages.orEmpty().any { languageFilter ->
  review.language.orEmpty().lowercase()
    .startsWith(languageFilter.lowercase())
}
