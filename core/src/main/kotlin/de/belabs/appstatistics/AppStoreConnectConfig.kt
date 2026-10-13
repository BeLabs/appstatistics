package de.belabs.appstatistics

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable data class AppStoreConnectConfig(
  @SerialName("issuer_id") val issuerId: String,
  @SerialName("key_id") val keyId: String,
  @SerialName("private_key_pem") val privateKeyPem: String,
) {
  init {
    requireNotNull(Uuid.parseOrNull(issuerId)) {
      "Issuer Id: $issuerId is invalid. Not a valid UUID"
    }

    require(keyId.length == 10) {
      "Key Id: $keyId is invalid, must be 10 characters long"
    }
  }

  companion object {
    val EXAMPLE = AppStoreConnectConfig(
      issuerId = "a6eba83e-9071-4789-b798-fea29e74c80e",
      keyId = "K9V2X7M4P1",
      privateKeyPem = """
        -----BEGIN PRIVATE KEY-----
        ...
        -----END PRIVATE KEY-----
      """.trimIndent(),
    )
  }
}
