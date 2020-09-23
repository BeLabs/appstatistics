package de.belabs.appstatistics.storereviews

import java.time.Instant

internal val review1Star = Review(
  id = "review-1-stars",
  content = "Really nice app. 1 stars",
  rating = 1,
  version = "1.0.0",
  author = "Me",
  updated = Instant.ofEpochSecond(1600844079)
)

internal val review5Stars = Review(
  id = "review-5-stars",
  title = "5 Stars title",
  content = "Really nice app. 5 stars",
  rating = 5,
  version = "1.0.0",
  author = "Me",
  updated = Instant.ofEpochSecond(1600844079)
)
