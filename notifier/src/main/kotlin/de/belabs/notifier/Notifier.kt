package de.belabs.notifier

interface Notifier<Configuration, Payload> {
  val configuration: Configuration

  fun name(): String

  fun emoji(): String

  suspend fun notify(payload: Payload)
}
