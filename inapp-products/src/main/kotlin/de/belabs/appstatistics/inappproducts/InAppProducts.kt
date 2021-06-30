package de.belabs.appstatistics.inappproducts

import com.google.api.services.androidpublisher.model.InAppProduct
import de.belabs.appstatistics.CoreCommand
import de.belabs.appstatistics.inappproducts.store.PlayStore
import de.belabs.appstatistics.inappproducts.store.Store
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
        val appOutput = directory.resolve("${app.name}/${store.name()}")
        appOutput.mkdirs()

        query(store, app, appOutput)

        val createDirectory = appOutput.resolve("create/")
        createDirectory.mkdirs()

        val inappProductsToCreate = createDirectory.listFiles { current, name ->
          val file = current.resolve(name)
          file.extension == "json" && file.length() > 0
        }.orEmpty().toList()

        create(store, app, appOutput, inappProductsToCreate)
      }
    }
  }

  private suspend fun create(
    store: Store,
    app: App,
    appOutput: File,
    inappProducts: List<File>,
  ) {
    if (inappProducts.isNotEmpty()) {
      logger.log("""🚧 Found ${inappProducts.size} product(s) for ${store.name()} ${app.name}""")
      logger.increaseIndent()

      inappProducts.forEach {
        val identifier = it.name
        try {
          logger.log("Trying to create $identifier")
          val inAppProduct = store.create(app, it)
          logger.log("✅ Successfully created $identifier")
          appOutput.write(inAppProduct)
          it.delete()
        } catch (throwable: Throwable) {
          logger.log("🔴 Failed creating $identifier")
          throwable.printStackTrace()
        }
      }

      logger.log()
      logger.decreaseIndent()
    }
  }

  private suspend fun query(
    store: Store,
    app: App,
    appOutput: File,
  ) {
    logger.log("""🔍 Querying ${store.name()} ${app.name} products""")
    logger.increaseIndent()

    val inAppProducts = store.inAppProducts(app)

    if (inAppProducts.isEmpty()) {
      logger.log("""⚠️ Found no in app products""")
    }

    inAppProducts.forEach { appOutput.write(it) }

    logger.log()
    logger.decreaseIndent()
  }

  fun File.write(inAppProduct: InAppProduct) {
    val file = resolve("${inAppProduct.sku}.json")
    logger.log("""✍️ Writing ${inAppProduct.sku} to $file""")
    file.writeText(inAppProduct.toPrettyString())
  }
}
