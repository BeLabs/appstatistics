package de.belabs.appstatistics

fun String?.takeIfNotBlank() = takeIf { it?.isNotBlank() == true }
