package de.belabs.appstatistics.inappproducts

import de.belabs.appstatistics.CoreCommand
import de.belabs.appstatistics.inappproducts.store.PlayStore
import de.belabs.appstatistics.jsonPretty
import kotlinx.serialization.builtins.ListSerializer
import java.io.File

internal class InAppProducts : CoreCommand() {
  override suspend fun runOn(
    organization: String,
    root: File
  ) {
    val directory = root.resolve("inapp-products/")
    directory.mkdirs()

    // Validate apps.
    val appsFile = directory.resolve("apps.json")

    if (!appsFile.exists()) {
      logger.log("""🔴 $appsFile is missing. Please configure your apps and re-run this script.""")
      logger.log("Here's an example configuration:")
      logger.log(jsonPretty.encodeToString(ListSerializer(App.serializer()), listOf(App.EXAMPLE)))
      return
    }

    val appsJson = appsFile.takeIf { it.exists() }?.readText() ?: "[]"
    val apps = jsonPretty.decodeFromString(ListSerializer(App.serializer()), appsJson)

    if (apps.isEmpty()) {
      logger.log("""⚠️ $appsFile contains no apps""")
    }

    // For now only Android is supported.
    val playStoreFile = playStoreFile(root) ?: return

    // Stores.
    val stores = listOfNotNull(
      PlayStore(playStoreFile),
    )

    apps.forEach { app ->
      stores.forEach { store ->
        logger.log("""⭐ Querying ${store.name()} ${app.name} products""")
        logger.increaseIndent()

        val appOutput = directory.resolve("${app.name}/${store.name()}")
        appOutput.mkdirs()
        val inAppProducts = store.inAppProducts(app)

        inAppProducts.forEach {
          val file = appOutput.resolve("${it.sku}.json")
          logger.log("""✍️ Writing $file""")
          file.writeText(it.toPrettyString())
        }

        logger.log()
        logger.decreaseIndent()
      }
    }
  }
}
