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
        val appOutput = appDirectory.resolve(store.name()).apply { mkdirs() }

        create(
          store,
          app,
          appOutput,
          appOutput.resolve("create/").apply { mkdirs() }
            .listFiles { file -> file.extension == "json" && file.length() > 0 }
            .orEmpty()
            .toList(),
        )

        edit(
          store,
          app,
          appOutput,
          appOutput.resolve("edit/").apply { mkdirs() }
            .listFiles { file -> file.extension == "json" && file.length() > 0 }
            .orEmpty()
            .toList(),
        )

        query(store, app, appOutput)
      }

      appDirectory
    }.toSet()

    val allAppDirectories = directory.listFiles { file -> file.isDirectory && !file.isHidden }
      .orEmpty()
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
    inAppProducts: List<File>,
  ) {
    if (inAppProducts.isNotEmpty()) {
      logger.log("""🚧 Found ${inAppProducts.size} creatable product(s) for ${store.name()} ${app.name}""")
      logger.increaseIndent()

      inAppProducts.forEach {
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

  private suspend fun edit(
    store: Store,
    app: App,
    appOutput: File,
    inAppProducts: List<File>,
  ) {
    if (inAppProducts.isNotEmpty()) {
      logger.log("""🚧 Found ${inAppProducts.size} editable product(s) for ${store.name()} ${app.name}""")
      logger.increaseIndent()

      inAppProducts.forEach {
        val identifier = it.name
        try {
          logger.log("Trying to edit $identifier")
          val inAppProduct = store.edit(app, it)
          logger.log("✅ Successfully edited $identifier")
          appOutput.write(inAppProduct)
          it.delete()
        } catch (throwable: Throwable) {
          logger.log("🔴 Failed editing $identifier")
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
    val appPrefix = app.name.snakecase()

    val localeInAppProducts = inAppProducts.flatMap { inAppProduct ->
      val prefix = "${appPrefix}_${inAppProduct.sku}_"
      inAppProduct.listings.flatMap { (locale, inAppProductListing) ->
        listOf(
          LocalisedInAppProduct(
            locale = locale,
            name = "${prefix}description",
            value = inAppProductListing.description,
          ),
          LocalisedInAppProduct(
            locale = locale,
            name = "${prefix}title",
            value = inAppProductListing.title,
          ),
        )
      }
    }.groupBy { it.locale }

    writeInAppProductsFile(localeInAppProducts, appOutput, app)
    writeAndroidResourcesStringsFile(localeInAppProducts, app)
  }

  private fun writeInAppProductsFile(
    localeInAppProducts: Map<String, List<LocalisedInAppProduct>>,
    appOutput: File,
    app: App,
  ) {
    val stringsDirectory = appOutput.resolve("strings/")
    stringsDirectory.delete()

    localeInAppProducts
      .forEach { (locale, localisedInAppProducts) ->
        val directory = stringsDirectory.resolve(stringsDirectoryFrom(locale))
        directory.mkdirs()

        val strings = localisedInAppProducts.sortedBy { it.name }
          .map { it.toString() }
          .joinToString(separator = "\n") { "${app.indentation}$it" }

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

  private fun writeAndroidResourcesStringsFile(
    localeInAppProducts: Map<String, List<LocalisedInAppProduct>>,
    app: App,
  ) {
    val androidResourceDirectory = app.androidResourceDirectory?.let(::File)

    if (androidResourceDirectory != null) {
      if (!androidResourceDirectory.exists()) {
        logger.log("""❌️ Android resource directory does not exist: $androidResourceDirectory""")
      } else {
        val valuesDirectories = androidResourceDirectory.listFiles { file ->
          file.isDirectory && file.name.startsWith("values") && !Regex("sw[\\d]+dp").containsMatchIn(file.name) && !file.name.startsWith("values-night")
        }.orEmpty()

        valuesDirectories.forEach { valuesDirectory ->
          val stringsFile = valuesDirectory.resolve(app.androidResourceStringsFileName)

          if (!stringsFile.exists()) {
            logger.log("""❌️ Android's ${app.androidResourceStringsFileName} file does not exist: $stringsFile""")
          } else {
            val valuesDirectoryName = valuesDirectory.name
            val locale = LOCALE_VALUES_MAP.firstNotNullOfOrNull { (locale, directoryName) -> locale.takeIf { directoryName == valuesDirectoryName } } ?: error("Unsupported values directory $valuesDirectoryName which can't be mapped into a locale")
            val inAppProducts = localeInAppProducts[locale] ?: error("Inapp products are not translated for $locale")
            val allInAppProducts = inAppProducts.toMutableList()

            stringsFile.writeText(
              stringsFile.readLines().dropLast(1).joinToString(separator = "\n") { line ->
                val match = inAppProducts.firstOrNull { line.contains(it.name) }
                when {
                  match != null -> {
                    allInAppProducts.remove(match)
                    "${app.indentation}$match"
                  }
                  else -> line
                }
              } + "\n" + allInAppProducts.joinToString(separator = "") { "${app.indentation}$it\n" } + "</resources>\n"
            )
          }
        }
      }
    }
  }

  private fun stringsDirectoryFrom(locale: String) = LOCALE_VALUES_MAP[locale] ?: error("Unsupported locale $locale which can't be mapped into a strings directory")

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
    val locale: String,
    val name: String,
    val value: String,
  ) {
    override fun toString() = """<string name="$name">$value</string>"""
  }

  companion object {
    val LOCALE_VALUES_MAP = mapOf(
      "en-US" to "values",
      "ar" to "values-ar",
      "bg" to "values-bg",
      "cs-CZ" to "values-cs",
      "de-DE" to "values-de",
      "el-GR" to "values-el",
      "es-ES" to "values-es",
      "fi-FI" to "values-fi",
      "fr-FR" to "values-fr",
      "hu-HU" to "values-hu",
      "id" to "values-in",
      "it-IT" to "values-it",
      "iw-IL" to "values-iw",
      "nl-NL" to "values-nl",
      "no-NO" to "values-no",
      "pl-PL" to "values-pl",
      "pt-BR" to "values-pt",
      "pt-PT" to "values-pt-rBR",
      "ro" to "values-ro",
      "ru-RU" to "values-ru",
      "sv-SE" to "values-sv",
      "tr-TR" to "values-tr",
      "uk" to "values-uk",
      "vi" to "values-vi",
      "zh-CN" to "values-zh-rCN",
      "zh-TW" to "values-zh-rTW",
    )
  }
}

// Not the best but does the job.
private fun String.snakecase() = replace(" ", "_")
  .lowercase()
