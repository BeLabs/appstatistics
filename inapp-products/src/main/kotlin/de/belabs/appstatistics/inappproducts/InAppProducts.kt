package de.belabs.appstatistics.inappproducts

import com.vanniktech.locale.Country
import com.vanniktech.locale.Language
import com.vanniktech.locale.Locale
import com.vanniktech.locale.google.play.store.googlePlayStoreLocale
import de.belabs.appstatistics.CoreCommand
import de.belabs.appstatistics.inappproducts.store.PlayStore
import de.belabs.appstatistics.inappproducts.store.Store
import de.belabs.appstatistics.inappproducts.store.StoreInAppProduct
import de.belabs.appstatistics.inappproducts.store.StoreInAppProductListing
import de.belabs.appstatistics.jsonPretty
import kotlinx.serialization.builtins.ListSerializer
import java.io.File

// TODO(nik)
var foo = mapOf<String, Map<String, StoreInAppProductListing>>()

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
        val updatedInAppProducts = updateInAppProductsFromApp(store, app, appOutput, inAppProducts)

        maybeUpdateApple(
          root = root,
          app = app,
          inAppProducts = updatedInAppProducts ?: inAppProducts,
        )

        if (updatedInAppProducts != null) {
          writeStringsFile(app, appOutput, updatedInAppProducts)
        }

        if (app.name == "Rss Reader") { // TODO(nik)
          foo = inAppProducts.associate { it.id to it.listings }
        }

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
  ): List<StoreInAppProduct> {
    logger.log("""🔍 Querying ${store.name()} ${app.name} products""")
    logger.increaseIndent()

    val inAppProducts = store.inAppProducts(app)
    val hasInAppProducts = inAppProducts.isNotEmpty()
    if (!hasInAppProducts) {
      logger.log("""⚠️ Found no in app products""")
    }

    writeFiles(appOutput, inAppProducts)

    // if (hasInAppProducts) {
    //   writeStringsFile(app, appOutput, inAppProducts)
    // }

    logger.log()
    return inAppProducts
  }

  /**
   * It looks at the strings xml files from the [app] and checks if any of the [inAppProducts]
   * have missing translations.
   * In this case, they have been added, and we want to update the listings also on the Play Console.
   * Returns the updated [StoreInAppProduct] or null if no changes have been made.
   */
  private suspend fun updateInAppProductsFromApp(
    store: Store,
    app: App,
    appOutput: File,
    inAppProducts: List<StoreInAppProduct>,
  ): List<StoreInAppProduct>? {
    val androidResourceDirectory = app.androidResourceDirectory?.let(::File)

    if (androidResourceDirectory != null && androidResourceDirectory.exists()) {
      val valuesDirectories = androidResourceDirectory.valuesDirectories()

      val modifiedInAppProducts = inAppProducts.mapNotNull { inAppProduct ->
        val listings = inAppProduct.listings.toMutableMap()

        valuesDirectories.map { valuesDirectory ->
          val locale = valuesDirectory.googlePlayStoreLocale().toString()
          val stringsFile = valuesDirectory.resolve(app.androidResourceStringsFileName)
          val stringsContent = stringsFile.readLines()
          val linePrefixDescription = "${app.indentation}<string name=\"${resourceDescription(app, inAppProduct)}"
          val linePrefixTitle = "${app.indentation}<string name=\"${resourceTitle(app, inAppProduct)}"
          val description = stringsContent.firstOrNull { it.startsWith(linePrefixDescription) }?.removePrefix(linePrefixDescription)?.substringAfter("\">")?.removeSuffix("</string>")?.xmlUnescaped()
          val title = stringsContent.firstOrNull { it.startsWith(linePrefixTitle) }?.removePrefix(linePrefixTitle)?.substringAfter("\">")?.removeSuffix("</string>")?.xmlUnescaped()
          val current = inAppProduct.listings[locale]
          val willChange = current?.title != title || current?.description != description

          if (willChange) {
            if (current?.title != title) {
              logger.log("""[${inAppProduct.id}#$locale] Changed title from "${current?.title}" to "$title"""")
            }

            if (current?.description != description) {
              logger.log("""[${inAppProduct.id}#$locale] Changed description from "${current?.description}" to "$description"""")
            }

            listings += locale to StoreInAppProductListing(
              title = title.orEmpty(),
              description = description.orEmpty(),
            )
          } else {
            null
          }
        }

        val test = foo[inAppProduct.id] // TODO(nik)

        if (test != null && test != inAppProduct.listings) {
          inAppProduct.copy(listings = test)
        } else if (listings != inAppProduct.listings) {
          inAppProduct.copy(listings = listings)
        } else {
          null
        }
      }

      if (modifiedInAppProducts.isNotEmpty()) {
        logger.increaseIndent()
        logger.log("""🕵️‍️ Detected ${modifiedInAppProducts.size} change(s) in app products from your Android code: ${modifiedInAppProducts.joinToString { it.id } }""")

        val canUpdate = !app.androidSuspended

        val modifiedIds = modifiedInAppProducts.map { it.id }.toSet()
        val unaffected = inAppProducts.filterNot { modifiedIds.contains(it.id) }

        val updated = unaffected + when {
          canUpdate -> {
            logger.log("""🔄 Updating Play Store""")
            modifiedInAppProducts.map {
              val inAppProduct = store.edit(app, it)
              appOutput.write(inAppProduct)
              inAppProduct
            }
          }
          else -> {
            logger.log("""🥺 Can't update Play Store because App is suspended""")
            modifiedInAppProducts
          }
        }

        writeStringsFile(
          app = app,
          appOutput = appOutput,
          inAppProducts = updated,
        )

        logger.log()
        logger.decreaseIndent()
        return updated
      }
    }

    return null
  }

  private suspend fun maybeUpdateApple(
    root: File,
    app: App,
    inAppProducts: List<StoreInAppProduct>,
  ) {
    if (app.appleAppId != null) {
      val config = appStoreConnectConfig(root) ?: return
      val client = AppStoreConnectClient(
        config = config,
      )

      try {
        logger.log("""🍏️ Apple's turn""")

        val inAppPurchases = client.getInAppPurchases(
          appAppleId = app.appleAppId,
        )

        inAppProducts
          .forEach { inAppProduct ->
            val match = inAppPurchases.data.firstOrNull { inAppProduct.id in it.attributes.productId }

            if (match != null) {
              val currentLocalizations = client.getInAppPurchaseLocalizations(match.id).data.associate {
                appStoreLocaleOrNull(it.attributes.locale) to StoreInAppProductListing(
                  title = it.attributes.name,
                  description = it.attributes.description,
                )
              }
                .filterNotNullKeys()

              val localizations = inAppProduct.listings
                .mapKeys { appStoreLocaleOrNull(it.key) }
                .filterNotNullKeys()

              if (localizations != currentLocalizations) {
                val appleErrors = inAppProduct.appleErrorsOrNull()

                if (appleErrors == null) {
                  val result = client.updateInAppPurchaseLocalizations(
                    iapId = match.id,
                    localizations = localizations,
                  )
                  logger.log("""🕵️‍️ Detected changes for ${inAppProduct.id} and $result""")
                } else {
                  logger.log("""🕵️‍️ Detected changes for ${inAppProduct.id} but can't change it due to: $appleErrors""")
                }
              } else {
                logger.log("""✅ All Apple localizations are up to date for ${inAppProduct.id}""")
              }
            } else {
              logger.log("""⚠️ Found no matching apple in app purchase for ${inAppProduct.id}""")
            }
          }
      } finally {
        client.close()
      }
    } else {
      logger.log("""🍏️ No apple id configured""")
    }
  }

  private fun resourcePrefix(app: App, inAppProduct: StoreInAppProduct) = "${app.name.snakecase()}_inapp_${inAppProduct.id.replace(".", "_")}_"

  private fun resourceTitle(app: App, inAppProduct: StoreInAppProduct) = "${resourcePrefix(app, inAppProduct)}title"

  private fun resourceDescription(app: App, inAppProduct: StoreInAppProduct) = "${resourcePrefix(app, inAppProduct)}description"

  private fun writeStringsFile(
    app: App,
    appOutput: File,
    inAppProducts: List<StoreInAppProduct>,
  ) {
    val localeInAppProducts = inAppProducts.flatMap { inAppProduct ->
      inAppProduct.listings.flatMap { (localeString, inAppProductListing) ->
        val locale = Locale.from(localeString)
        listOf(
          LocalisedInAppProduct(
            id = inAppProduct.id,
            locale = locale,
            name = resourceDescription(app, inAppProduct),
            value = inAppProductListing.description,
          ),
          LocalisedInAppProduct(
            id = inAppProduct.id,
            locale = locale,
            name = resourceTitle(app, inAppProduct),
            value = inAppProductListing.title,
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
        val territory = locale.territory
        val directory = stringsDirectory.resolve(
          when {
            language == Language.ENGLISH && territory == Country.USA -> "values"
            language.defaultCountry != territory && territory != null -> "values-$languageCode-r${territory.code}"
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
                      "${app.indentation}${match.xml(isUnused = IGNORE_UNUSED_RESOURCES in line)}"
                    }
                    else -> line
                  }
                } + "\n" + allInAppProducts.joinToString(separator = "") { "${app.indentation}$it\n" } + "</resources>\n",
              )
            }

            locale
          }
        }

        localeInAppProducts.groupBy { it.id }
          .forEach { (id, inAppProducts) ->
            val diff = allLocales - inAppProducts.map { it.locale }.toSet()

            if (diff.isNotEmpty()) {
              logger.log("""⚠️ Missing translations for $id: ${diff.joinToString(separator = ", ")}""")
            }
          }

        return androidResourceDirectory
      }
    }

    return null
  }

  private fun writeFiles(
    appOutput: File,
    inAppProducts: List<StoreInAppProduct>,
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

  private fun File.write(inAppProduct: StoreInAppProduct): File {
    val file = resolve("${inAppProduct.id}.json")
    logger.log("""✍️ Writing ${inAppProduct.id} to $file""")
    file.writeText(inAppProduct.prettyString)
    return file
  }

  private data class LocalisedInAppProduct(
    val id: String,
    val locale: Locale,
    val name: String,
    val value: String,
  ) {
    fun xml(isUnused: Boolean) = """<string name="$name"${if (isUnused) " $IGNORE_UNUSED_RESOURCES" else ""}>${value.xmlEscaped()}</string>"""
    override fun toString() = xml(isUnused = false)
  }
}

// Not the best but does the job.
private fun String.snakecase() = replace(" ", "_")
  .lowercase()

private fun String.xmlEscaped() = replace("'", "’")
private fun String.xmlUnescaped() = replace("\\'", "'")

private const val IGNORE_UNUSED_RESOURCES = "tools:ignore=\"UnusedResources\""
