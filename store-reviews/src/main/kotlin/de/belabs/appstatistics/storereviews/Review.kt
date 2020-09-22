package de.belabs.appstatistics.storereviews

import kotlinx.serialization.Decoder
import kotlinx.serialization.Encoder
import kotlinx.serialization.PrimitiveDescriptor
import kotlinx.serialization.PrimitiveKind.LONG
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Serializer
import java.time.Instant

@Serializable internal data class Review(
  @SerialName("id") val id: String,
  @SerialName("title") val title: String?,
  @SerialName("content") val content: String,
  @SerialName("rating") val rating: Int,
  @SerialName("version") val version: String,
  @SerialName("author") val author: String,
  @SerialName("updated") @Serializable(with = InstantSerializer::class) val updated: Instant
)

@Serializer(forClass = Instant::class) private object InstantSerializer {
  override val descriptor get() = PrimitiveDescriptor("InstantSerializer", LONG)

  override fun deserialize(decoder: Decoder) = Instant.ofEpochMilli(decoder.decodeLong())

  override fun serialize(encoder: Encoder, value: Instant) {
    encoder.encodeLong(value.toEpochMilli())
  }
}
