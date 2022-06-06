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

    val appDirectories = apps.map { app ->
      val appDirectory = directory.resolve(app.name)

      stores.forEach { store ->
        val appOutput = appDirectory.resolve(store.name())
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

      appDirectory
    }.toSet()

    val allAppDirectories = directory.listFiles { file -> file.isDirectory && !file.isHidden }
      .toSet()
    val diffAppDirectories = allAppDirectories - appDirectories

    if (diffAppDirectories.isNotEmpty()) {
      diffAppDirectories.sortedBy { it }
        .forEach {
          logger.log("""⚠️ Could not find matching app in apps.json for $it""")
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
    val hasInAppProducts = inAppProducts.isNotEmpty()
    if (!hasInAppProducts) {
      logger.log("""⚠️ Found no in app products""")
    }

    writeFiles(appOutput, inAppProducts)

    if (hasInAppProducts) {
      writeStringsFile(app, appOutput, inAppProducts)
    }

    logger.log()
    logger.decreaseIndent()
  }

  private fun writeStringsFile(
    app: App,
    appOutput: File,
    inAppProducts: List<InAppProduct>,
  ) {
    val stringsDirectory = appOutput.resolve("strings/")
    stringsDirectory.delete()

    inAppProducts.flatMap { inAppProduct ->
      inAppProduct.listings.map { (locale, inAppProductListing) ->
        LocalisedInAppProduct(
          sku = inAppProduct.sku,
          locale = locale,
          title = inAppProductListing.title,
          description = inAppProductListing.description,
        )
      }
    }
      .groupBy { it.locale }
      .forEach { (locale, localisedInAppProducts) ->
        val directory = stringsDirectory.resolve(stringsDirectoryFrom(locale))
        directory.mkdirs()

        val strings = localisedInAppProducts.sortedBy { it.sku }
          .flatMap {
            val prefix = app.name.snakecase()
            listOf(
              """<string name="${prefix}_${it.sku}_description">${it.description}</string>""",
              """<string name="${prefix}_${it.sku}_title">${it.title}</string>""",
            )
          }
          .joinToString(separator = "\n") { "  $it" }

        directory.resolve("inapp-products.xml").writeText(
          """
          |<?xml version="1.0" encoding="utf-8"?>
          |<resources>
          |$strings
          |</resources>
          |
          """.trimMargin()
        )
      }

    logger.log("""🎈️ Wrote all titles & descriptions of all in app products to $stringsDirectory""")
  }

  private fun stringsDirectoryFrom(locale: String) = when (locale) {
    "en-US" -> "values"
    "ar" -> "values-ar"
    "bg" -> "values-bg"
    "de-DE" -> "values-de"
    "el-GR" -> "values-el"
    "es-ES" -> "values-es"
    "fi-FI" -> "values-fi"
    "fr-FR" -> "values-fr"
    "hu-HU" -> "values-hu"
    "id" -> "values-in"
    "it-IT" -> "values-it"
    "iw-IL" -> "values-iw"
    "nl-NL" -> "values-nl"
    "no-NO" -> "values-no"
    "pt-BR" -> "values-pt"
    "pt-PT" -> "values-pt-rBR"
    "ro" -> "values-ro"
    "ru-RU" -> "values-ru"
    "sv-SE" -> "values-sv"
    "tr-TR" -> "values-tr"
    "uk" -> "values-uk"
    "vi" -> "values-vi"
    "zh-CN" -> "values-zh-rCN"
    "zh-TW" -> "values-zh-rTW"
    else -> error("Unsupported locale $locale which can't be mapped into a strings directory")
  }

  private fun writeFiles(
    appOutput: File,
    inAppProducts: List<InAppProduct>,
  ) {
    val deletedFiles = appOutput.listFiles { file -> file.extension == "json" }.toSet()
    deletedFiles.forEach { it.delete() }

    val createdFiles = inAppProducts.map { inAppProduct -> appOutput.write(inAppProduct) }.toSet()
    val fileDiff = deletedFiles - createdFiles

    if (fileDiff.isNotEmpty()) {
      fileDiff.sortedBy { it }
        .forEach {
          logger.log("""🗑️️ Deleted as no longer in use: $it""")
        }
    }
  }

  private fun File.write(inAppProduct: InAppProduct): File {
    val file = resolve("${inAppProduct.sku}.json")
    logger.log("""✍️ Writing ${inAppProduct.sku} to $file""")
    file.writeText(inAppProduct.toPrettyString())
    return file
  }

  private data class LocalisedInAppProduct(
    val sku: String,
    val locale: String,
    val title: String,
    val description: String,
  )
}

// Not the best but does the job.
private fun String.snakecase() = replace(" ", "_")
  .lowercase()
