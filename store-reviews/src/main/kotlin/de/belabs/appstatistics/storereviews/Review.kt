package de.belabs.appstatistics.storereviews

import kotlinx.datetime.Instant
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind.LONG
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable internal data class Review(
  @SerialName("id") val id: String,
  @SerialName("title") val title: String? = null,
  @SerialName("language") val language: String? = null,
  @SerialName("content") val content: String,
  @SerialName("rating") val rating: Int,
  @SerialName("version") val version: String? = null,
  @SerialName("author") val author: String,
  @SerialName("updated") @Serializable(with = InstantEpochMillisecondsSerializer::class) val updated: Instant,
)

private object InstantEpochMillisecondsSerializer : KSerializer<Instant> {
  override val descriptor get() = PrimitiveSerialDescriptor("InstantSerializer", LONG)

  override fun serialize(
    encoder: Encoder,
    value: Instant,
  ) {
    encoder.encodeLong(value.toEpochMilliseconds())
  }

  override fun deserialize(decoder: Decoder) = Instant.fromEpochMilliseconds(decoder.decodeLong())
}
