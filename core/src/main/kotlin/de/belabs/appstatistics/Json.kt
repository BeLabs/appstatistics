package de.belabs.appstatistics

import kotlinx.serialization.json.Json

val jsonPretty = Json {
  prettyPrint = true
  prettyPrintIndent = "  "
}
