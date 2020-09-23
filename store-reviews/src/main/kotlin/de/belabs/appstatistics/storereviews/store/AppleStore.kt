package de.belabs.appstatistics.storereviews.store

import de.belabs.appstatistics.storereviews.App
import de.belabs.appstatistics.storereviews.Review
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.features.defaultRequest
import io.ktor.client.request.get
import io.ktor.http.URLProtocol
import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.StringReader
import java.time.OffsetDateTime
import javax.xml.parsers.DocumentBuilderFactory

internal class AppleStore : Store {
  private val httpClient = HttpClient(OkHttp.create()) {
    defaultRequest {
      url.protocol = URLProtocol.HTTPS
      url.host = "itunes.apple.com"
    }
  }

  override fun name() = "iOS"

  override suspend fun reviews(app: App): List<Review> {
    if (app.appleId != null) {
      return app.appleLanguagesToCheck.flatMap { language ->
        (1..app.appleNumberOfPagesToCheck)
          .flatMap { pageNumber ->
            val response = httpClient.get<String>("$language/rss/customerreviews/page=$pageNumber/id=${app.appleId}/sortby=mostrecent/xml?urlDesc=/customerreviews/id=${app.appleId}/xml")

            val newDocumentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            val document = newDocumentBuilder.parse(InputSource(StringReader(response)))
            document.documentElement.normalize()

            val items = document.getElementsByTagName("entry")

            (0 until items.length)
              .map { items.item(it) }
              .filterIsInstance<Element>()
              .map {
                Review(
                  id = it.string("id"),
                  title = it.string("title"),
                  content = it.string("content"),
                  language = language,
                  updated = OffsetDateTime.parse(it.string("updated")).toInstant(),
                  rating = it.string("im:rating").toInt(),
                  version = it.string("im:version"),
                  author = (it.getElementsByTagName("author").item(0) as Element).string("name")
                )
              }
          }
      }
    }

    return emptyList()
  }
}

private fun Element.string(name: String) = getElementsByTagName(name).item(0).textContent
