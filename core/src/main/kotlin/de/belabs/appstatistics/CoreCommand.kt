package de.belabs.appstatistics

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import java.io.File

abstract class CoreCommand : CliktCommand() {
  protected val directoryAppStatistics: File by option(help = "App statistics directory")
      .convert { File(it) }
      .default(File(System.getProperty("user.home")).resolve(".appstatistics"))

  protected val logger = Logger()

  final override fun run() = runBlocking {
    logger.log("""🔎 Scanning for organizations in $directoryAppStatistics""")
    logger.increaseIndent()

    val organizations = directoryAppStatistics.listFiles { current, name ->
      current.resolve(name).isDirectory
    }.orEmpty()
      .filterNot { it.isHidden }

    if (organizations.isNotEmpty()) {
      logger.log("""📒️ Found ${organizations.joinToString { it.name }}""")
      logger.increaseIndent()

      organizations.forEach { directory ->
        val organizationName = directory.name
        logger.log("""📒️ Running $organizationName""")
        logger.increaseIndent()
        runOn(organizationName, directory)
        yield() // Keep the order.

        logger.decreaseIndent()
        logger.log()
      }
    } else {
      logger.log("""🔴 No organizations found. Please create a directory named according to your organization in $directoryAppStatistics and re-run this script.""")
    }
  }

  abstract suspend fun runOn(organization: String, root: File)

  protected fun playStoreFile(directory: File): File? {
    val playStoreFile = directory.resolve("play-store.json")

    if (!playStoreFile.exists()) {
      logger.log("""🔴 In order to get reviews from the Play Store we need a Service account file at $playStoreFile""")
      logger.log("Follow these instructions to create one: https://github.com/Triple-T/gradle-play-publisher#service-account")
      return null
    }

    return playStoreFile
  }
}
