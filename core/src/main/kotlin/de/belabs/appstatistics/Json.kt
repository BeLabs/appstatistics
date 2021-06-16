package de.belabs.appstatistics

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json

@OptIn(ExperimentalSerializationApi::class)
val jsonPretty = Json {
  prettyPrint = true
  prettyPrintIndent = "  "
}
