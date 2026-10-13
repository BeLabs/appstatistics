package de.belabs.appstatistics.inappproducts

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.vanniktech.locale.Locale
import com.vanniktech.locale.apple.app.store.AppleAppStoreLocale
import com.vanniktech.locale.apple.app.store.appleAppStoreLocale
import de.belabs.appstatistics.AppStoreConnectConfig
import de.belabs.appstatistics.inappproducts.store.StoreInAppProductListing
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.network.sockets.SocketTimeoutException
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.KeyFactory
import java.security.interfaces.ECPrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64
import java.util.Date
import kotlin.time.Duration.Companion.seconds

@Serializable data class JsonApiData<T>(
  val data: T,
)

@Serializable data class RelationshipData(
  val type: String,
  val id: String,
)

@Serializable data class Relationship(
  val data: RelationshipData,
)

@Serializable data class CreateVersionRequest(
  val type: String,
  val relationships: VersionRelationships,
)

@Serializable data class VersionRelationships(
  val inAppPurchase: Relationship,
)

@Serializable data class CreateLocalizationRequest(
  val type: String,
  val attributes: LocalizationAttributes,
  val relationships: LocalizationRelationships,
)

@Serializable
data class UpdateLocalizationRequest(
  val type: String,
  val id: String,
  val attributes: UpdateLocalizationAttributes,
)

@Serializable data class LocalizationRelationships(
  val version: Relationship,
)

@Serializable data class ResourceId(
  val id: String,
  val type: String,
)

@Serializable data class InAppPurchasesResponse(
  val data: List<IapResource>,
)

@Serializable data class IapResource(
  val id: String,
  val attributes: IapAttributes,
)

@Serializable data class IapAttributes(
  val productId: String,
)

@Serializable data class LocalizationsResponse(
  val data: List<LocalizationResource>,
)

@Serializable data class LocalizationResource(
  val id: String,
  val attributes: LocalizationAttributes,
)

@Serializable data class LocalizationAttributes(
  val locale: String,
  val name: String,
  val description: String,
  val state: String? = null,
)

@Serializable data class UpdateLocalizationAttributes(
  val name: String,
  val description: String,
)

@Serializable
data class VersionsResponse(
  val data: List<VersionResource>,
)

@Serializable
data class VersionResource(
  val id: String,
  val attributes: VersionAttributes,
)

@Serializable
data class VersionAttributes(
  val version: Int,
  val state: String,
)

class AppStoreConnectClient(
  private val config: AppStoreConnectConfig,
) {
  private val client = HttpClient(OkHttp.create { }) {
    install(ContentNegotiation) {
      json(
        Json {
          ignoreUnknownKeys = true
          prettyPrint = false
        },
      )
    }

    install(HttpTimeout) {
      // Apple sucks. We intentionally decrease it and rather try more times.
      socketTimeoutMillis = 5.seconds.inWholeMilliseconds
    }
  }

  private val baseUrl = "https://api.appstoreconnect.apple.com"

  private fun generateJwt(): String {
    val cleaned = config.privateKeyPem
      .replace("-----BEGIN PRIVATE KEY-----", "")
      .replace("-----END PRIVATE KEY-----", "")
      .replace("\\s".toRegex(), "")

    val keyBytes = Base64.getDecoder().decode(cleaned)
    val keySpec = PKCS8EncodedKeySpec(keyBytes)
    val privateKey = KeyFactory.getInstance("EC").generatePrivate(keySpec) as ECPrivateKey

    val algorithm = Algorithm.ECDSA256(null, privateKey)

    return JWT.create()
      .withKeyId(config.keyId)
      .withIssuer(config.issuerId)
      .withAudience("appstoreconnect-v1")
      .withIssuedAt(Date())
      .withExpiresAt(Date(System.currentTimeMillis() + 15 * 60 * 1000))
      .sign(algorithm)
  }

  suspend fun getInAppPurchases(
    appAppleId: Long,
  ) = client.get("$baseUrl/v1/apps/$appAppleId/inAppPurchasesV2") {
    header(HttpHeaders.Authorization, "Bearer ${generateJwt()}")
    parameter("limit", 200)
  }.body<InAppPurchasesResponse>()

  suspend fun getInAppPurchaseLocalizations(
    iapId: String,
  ) = client.get("$baseUrl/v2/inAppPurchases/$iapId/inAppPurchaseLocalizations") {
    header(HttpHeaders.Authorization, "Bearer ${generateJwt()}")
    parameter("limit", 50)
  }.body<LocalizationsResponse>()

  suspend fun updateInAppPurchaseLocalizations(
    iapId: String,
    localizations: Map<AppleAppStoreLocale, StoreInAppProductListing>,
  ): String {
    val token = generateJwt()

    // 1. Let's figure out which version we can use!
    val highestVersion = client.get("$baseUrl/v2/inAppPurchases/$iapId/versions") {
      header(HttpHeaders.Authorization, "Bearer $token")
    }.body<VersionsResponse>()
      .data
      .maxByOrNull { it.attributes.version }

    val versionId = when (val state = highestVersion?.attributes?.state) {
      null, "APPROVED" -> {
        val request = CreateVersionRequest(
          type = "inAppPurchaseVersions",
          relationships = VersionRelationships(
            inAppPurchase = Relationship(RelationshipData("inAppPurchases", iapId)),
          ),
        )

        val versionResponse = client.post("$baseUrl/v1/inAppPurchaseVersions") {
          header(HttpHeaders.Authorization, "Bearer $token")
          contentType(ContentType.Application.Json)
          setBody(JsonApiData(request))
        }.body<JsonApiData<ResourceId>>()

        versionResponse.data.id
      }
      "PREPARE_FOR_SUBMISSION", "READY_FOR_REVIEW" -> highestVersion.id
      "WAITING_FOR_REVIEW", "IN_REVIEW" -> return highestVersion.id // We can't do anything yet! We must wait until Apple completes it.
      else -> error("Unknown version state: $state")
    }

    // 2. Get the localizations for the current version.
    val versionLocalizations = client.get("$baseUrl/v1/inAppPurchaseVersions/$versionId/localizations") {
      header(HttpHeaders.Authorization, "Bearer $token")
    }.body<LocalizationsResponse>()

    // 3. Create or patch each localization.
    val results = localizations.map { (locale, listing) ->
      require(listing.description.length <= 55) {
        "$iapId $locale ${listing.description} is too long"
      }

      val existing = versionLocalizations.data.firstOrNull { it.attributes.locale == locale.toString() }

      if (existing != null) {
        // If it is re-run, we might have already updated it. In that case we don't want to do anything!
        val isDifferent = existing.attributes.name != listing.title || existing.attributes.description != listing.description

        !isDifferent || try {
          val response = client.patch("$baseUrl/v2/inAppPurchaseLocalizations/${existing.id}") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(
              JsonApiData(
                UpdateLocalizationRequest(
                  type = "inAppPurchaseLocalizations",
                  id = existing.id,
                  attributes = UpdateLocalizationAttributes(listing.title, listing.description),
                ),
              ),
            )
          }

          if (!response.status.isSuccess()) {
            error("$iapId $locale failed -> ${response.bodyAsText()}")
          }

          true
        } catch (_: SocketTimeoutException) {
          // Ignore for now. This is anyway incrementally possible.
          false
        }
      } else {
        try {
          val response = client.post("$baseUrl/v2/inAppPurchaseLocalizations") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(
              JsonApiData(
                CreateLocalizationRequest(
                  type = "inAppPurchaseLocalizations",
                  attributes = LocalizationAttributes(locale.toString(), listing.title, listing.description),
                  relationships = LocalizationRelationships(
                    version = Relationship(RelationshipData("inAppPurchaseVersions", versionId)),
                  ),
                ),
              ),
            )
          }

          if (!response.status.isSuccess()) {
            error("$iapId $locale failed -> ${response.bodyAsText()}")
          }

          true
        } catch (_: SocketTimeoutException) {
          // Ignore for now. This is anyway incrementally possible.
          false
        }
      }
    }

    val successful = results.filter { it }
    val success = successful.size == localizations.size

    return when (success) {
      true -> "submitted as $versionId"
      false -> "partially submitted (${successful.size} / ${localizations.size}), please re-run"
    }
  }

  fun close() = client.close()
}

@Suppress("UNCHECKED_CAST") fun <K, V> Map<K?, V>.filterNotNullKeys() = filterKeys { it != null } as Map<K, V>

fun appStoreLocaleOrNull(value: String) = Locale.fromOrNull(value)?.appleAppStoreLocale()
