package de.belabs.appstatistics.inappproducts

import com.google.api.services.androidpublisher.model.InAppProduct
import com.google.api.services.androidpublisher.model.InAppProductListing
import com.vanniktech.locale.Country
import com.vanniktech.locale.Language
import com.vanniktech.locale.Locale
import de.belabs.appstatistics.CoreCommand
import de.belabs.appstatistics.inappproducts.store.PlayStore
import de.belabs.appstatistics.inappproducts.store.Store
import de.belabs.appstatistics.jsonPretty
import kotlinx.serialization.builtins.ListSerializer
import java.io.File

internal class InAppProducts : CoreCommand() {
  override suspend fun runOn(
    organization: String,
    root: File,
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

        val inAppProducts = query(store, app, appOutput)
        updateMissingListingsFromApp(store, app, appOutput, inAppProducts)
        logger.decreaseIndent()
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
  ): List<InAppProduct> {
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
    return inAppProducts
  }

  /**
   * Looks at the strings xml files from the [app] and checks if any of the [inAppProducts]
   * have missing translations.
   * In this case, they have been added, and we want to update the listings also on the Play Console.
   */
  private suspend fun updateMissingListingsFromApp(
    store: Store,
    app: App,
    appOutput: File,
    inAppProducts: List<InAppProduct>,
  ) {
    val androidResourceDirectory = app.androidResourceDirectory?.let(::File)

    if (androidResourceDirectory != null && androidResourceDirectory.exists()) {
      val valuesDirectories = androidResourceDirectory.valuesDirectories()

      val modifiedInAppProducts = inAppProducts.mapNotNull { inAppProduct ->
        val hasChanged = valuesDirectories.map { valuesDirectory ->
          val locale = valuesDirectory.googlePlayStoreLocale().toString()
          val stringsFile = valuesDirectory.resolve(app.androidResourceStringsFileName)
          val stringsContent = stringsFile.readLines()
          val linePrefixDescription = "${app.indentation}<string name=\"${resourceDescription(app, inAppProduct)}"
          val linePrefixTitle = "${app.indentation}<string name=\"${resourceTitle(app, inAppProduct)}"
          val description = stringsContent.firstOrNull { it.startsWith(linePrefixDescription) }?.removePrefix(linePrefixDescription)?.removePrefix("\">")?.removeSuffix("</string>")?.xmlUnescaped()
          val title = stringsContent.firstOrNull { it.startsWith(linePrefixTitle) }?.removePrefix(linePrefixTitle)?.removePrefix("\">")?.removeSuffix("</string>")?.xmlUnescaped()
          val current = inAppProduct.listings[locale]
          val willChange = current?.title() != title || current?.description() != description
          val match = current ?: InAppProductListing()
          match.title = title
          match.description = description
          inAppProduct.listings = inAppProduct.listings + (locale to match)
          willChange
        }.any { it }

        inAppProduct.takeIf { hasChanged }
      }

      if (modifiedInAppProducts.isNotEmpty()) {
        logger.increaseIndent()
        logger.log("""🕵️‍️ Detected ${modifiedInAppProducts.size} change(s) in app products from your Android code: ${modifiedInAppProducts.joinToString { it.sku } }""")

        val modifiedInAppProductsSkus = modifiedInAppProducts.map { it.sku }.toSet()
        val others = inAppProducts.filterNot { modifiedInAppProductsSkus.contains(it.sku) }

        writeStringsFile(
          app = app,
          appOutput = appOutput,
          inAppProducts = others + modifiedInAppProducts.map {
            val inAppProduct = store.edit(app, it)
            appOutput.write(inAppProduct)
            inAppProduct
          },
        )

        logger.log()
        logger.decreaseIndent()
      }
    }
  }

  private fun resourcePrefix(app: App, inAppProduct: InAppProduct) =
    "${app.name.snakecase()}_inapp_${inAppProduct.sku}_"

  private fun resourceTitle(app: App, inAppProduct: InAppProduct) =
    "${resourcePrefix(app, inAppProduct)}title"

  private fun resourceDescription(app: App, inAppProduct: InAppProduct) =
    "${resourcePrefix(app, inAppProduct)}description"

  private fun writeStringsFile(
    app: App,
    appOutput: File,
    inAppProducts: List<InAppProduct>,
  ) {
    val localeInAppProducts = inAppProducts.flatMap { inAppProduct ->
      inAppProduct.listings.flatMap { (localeString, inAppProductListing) ->
        val locale = Locale.from(localeString)
        listOf(
          LocalisedInAppProduct(
            sku = inAppProduct.sku,
            locale = locale,
            name = resourceDescription(app, inAppProduct),
            value = inAppProductListing.description(),
          ),
          LocalisedInAppProduct(
            sku = inAppProduct.sku,
            locale = locale,
            name = resourceTitle(app, inAppProduct),
            value = inAppProductListing.title(),
          ),
        )
      }
    }

    val destinations = listOfNotNull(
      writeInAppProductsFile(localeInAppProducts, appOutput, app),
      writeAndroidResourcesStringsFile(localeInAppProducts, app),
    )

    logger.log("""🎈️ Wrote all titles & descriptions of all in app products to ${destinations.joinToString(separator = " & ")}""")
  }

  private fun writeInAppProductsFile(
    localeInAppProducts: List<LocalisedInAppProduct>,
    appOutput: File,
    app: App,
  ): File {
    val stringsDirectory = appOutput.resolve("strings/")
    stringsDirectory.delete()

    localeInAppProducts
      .groupBy { it.locale }
      .forEach { (locale, localisedInAppProducts) ->
        val language = locale.language
        val languageCode = language.legacyCode ?: language.code
        val country = locale.country
        val directory = stringsDirectory.resolve(
          when {
            language == Language.ENGLISH && country == Country.USA -> "values"
            language.defaultCountry != country && country != null -> "values-$languageCode-r${country.code}"
            else -> "values-$languageCode"
          },
        )
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
          """.trimMargin(),
        )
      }

    return stringsDirectory
  }

  private fun File.valuesDirectories() = listFiles { file ->
    file.isDirectory && file.name.startsWith("values") && !Regex("sw[\\d]+dp").containsMatchIn(file.name) && !file.name.startsWith("values-night") && !file.name.startsWith("values-land")
  }.orEmpty()

  private fun File.googlePlayStoreLocale() = Locale.fromAndroidValuesDirectoryName(name).googlePlayStoreLocale()!!

  private fun writeAndroidResourcesStringsFile(
    localeInAppProducts: List<LocalisedInAppProduct>,
    app: App,
  ): File? {
    val androidResourceDirectory = app.androidResourceDirectory?.let(::File)

    if (androidResourceDirectory != null) {
      if (!androidResourceDirectory.exists()) {
        logger.log("""❌️ Android resource directory does not exist: $androidResourceDirectory""")
      } else {
        val valuesDirectories = androidResourceDirectory.valuesDirectories()
        val localeInAppProductsLocaleMap = localeInAppProducts.groupBy { it.locale }
        val allLocales = valuesDirectories.mapNotNull { valuesDirectory ->
          val stringsFile = valuesDirectory.resolve(app.androidResourceStringsFileName)

          if (!stringsFile.exists()) {
            logger.log("""❌️ Android's ${app.androidResourceStringsFileName} file does not exist: $stringsFile""")
            null
          } else {
            val locale = Locale.from(valuesDirectory.googlePlayStoreLocale().toString())
            val inAppProducts = localeInAppProductsLocaleMap[locale]

            if (inAppProducts != null) {
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
                } + "\n" + allInAppProducts.joinToString(separator = "") { "${app.indentation}$it\n" } + "</resources>\n",
              )
            }

            locale
          }
        }

        localeInAppProducts.groupBy { it.sku }
          .forEach { (sku, inAppProducts) ->
            val diff = allLocales - inAppProducts.map { it.locale }.toSet()

            if (diff.isNotEmpty()) {
              logger.log("""⚠️ Missing translations for $sku: ${diff.joinToString(separator = ", ")}""")
            }
          }

        return androidResourceDirectory
      }
    }

    return null
  }

  private fun writeFiles(
    appOutput: File,
    inAppProducts: List<InAppProduct>,
  ) {
    val deletedFiles = appOutput.listFiles { file -> file.extension == "json" }.orEmpty().toSet()
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
    val locale: Locale,
    val name: String,
    val value: String,
  ) {
    override fun toString() = """<string name="$name">${value.xmlEscaped()}</string>"""
  }
}

// Not the best but does the job.
private fun String.snakecase() = replace(" ", "_")
  .lowercase()

private fun String.xmlEscaped() = replace("'", "’")
private fun String.xmlUnescaped() = replace("\\'", "'")

private fun InAppProductListing.title() = title.trim()
private fun InAppProductListing.description() = description.trim()
