package de.belabs.appstatistics.storereviews

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.Instant

@Serializable internal data class Review(
  @SerialName("id") val id: String,
  @SerialName("title") val title: String? = null,
  @SerialName("language") val language: String? = null,
  @SerialName("content") val content: String,
  @SerialName("rating") val rating: Int,
  @SerialName("version") val version: String? = null,
  @SerialName("author") val author: String,
  @SerialName("updated") @Serializable(with = InstantSerializer::class) val updated: Instant
)

@OptIn(ExperimentalSerializationApi::class)
@Serializer(forClass = Instant::class) private object InstantSerializer {
  override val descriptor get() = PrimitiveSerialDescriptor("InstantSerializer", PrimitiveKind.LONG)

  override fun deserialize(decoder: Decoder) = Instant.ofEpochMilli(decoder.decodeLong())

  override fun serialize(encoder: Encoder, value: Instant) {
    encoder.encodeLong(value.toEpochMilli())
  }
}
