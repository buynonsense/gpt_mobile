package dev.chungjungsoo.gptmobile.data.sync

import dev.chungjungsoo.gptmobile.data.sync.model.WebDavRemoteFile
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

object WebDavXmlParser {

    fun parse(xml: String): List<WebDavRemoteFile> {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
        }
        val document = factory.newDocumentBuilder().parse(xml.byteInputStream())
        val responseNodes = document.getElementsByTagNameNS("DAV:", "response")

        return buildList {
            for (index in 0 until responseNodes.length) {
                val response = responseNodes.item(index) as? Element ?: continue
                val href = response.getElementsByTagNameNS("DAV:", "href").item(0)?.textContent ?: continue
                val prop = response.findSuccessfulProp()
                val displayName = prop?.getElementsByTagNameNS("DAV:", "displayname")?.item(0)?.textContent
                val contentLength = prop?.getElementsByTagNameNS("DAV:", "getcontentlength")?.item(0)?.textContent?.toLongOrNull()
                val modifiedAt = prop?.getElementsByTagNameNS("DAV:", "getlastmodified")?.item(0)?.textContent
                val etag = prop?.getElementsByTagNameNS("DAV:", "getetag")?.item(0)?.textContent
                val normalizedName = displayName?.takeIf { it.isNotBlank() } ?: href.trimEnd('/').substringAfterLast('/')
                if (normalizedName.isBlank()) {
                    continue
                }
                add(
                    WebDavRemoteFile(
                        path = href,
                        name = normalizedName,
                        modifiedAt = modifiedAt,
                        contentLength = contentLength,
                        etag = etag
                    )
                )
            }
        }
    }

    private fun Element.findSuccessfulProp(): Element? {
        val propstatNodes = getElementsByTagNameNS("DAV:", "propstat")
        for (index in 0 until propstatNodes.length) {
            val propstat = propstatNodes.item(index) as? Element ?: continue
            val status = propstat.getElementsByTagNameNS("DAV:", "status").item(0)?.textContent.orEmpty()
            if (!status.contains(" 200 ")) {
                continue
            }
            return propstat.getElementsByTagNameNS("DAV:", "prop").item(0) as? Element
        }
        return null
    }
}
