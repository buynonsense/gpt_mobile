package dev.chungjungsoo.gptmobile.data.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WebDavXmlParserTest {

    @Test
    fun parse_readsDisplayNameAndMetadata_fromSuccessfulPropstat() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <d:multistatus xmlns:d="DAV:">
                <d:response>
                    <d:href>/remote/z-last.backup</d:href>
                    <d:propstat>
                        <d:prop>
                            <d:getcontentlength>999</d:getcontentlength>
                        </d:prop>
                        <d:status>HTTP/1.1 404 Not Found</d:status>
                    </d:propstat>
                    <d:propstat>
                        <d:prop>
                            <d:displayname>Z backup</d:displayname>
                            <d:getcontentlength>321</d:getcontentlength>
                            <d:getlastmodified>Tue, 19 Mar 2024 10:20:30 GMT</d:getlastmodified>
                            <d:getetag>"etag-z"</d:getetag>
                        </d:prop>
                        <d:status>HTTP/1.1 200 OK</d:status>
                    </d:propstat>
                </d:response>
                <d:response>
                    <d:href>/remote/a-first.backup</d:href>
                    <d:propstat>
                        <d:prop>
                            <d:displayname>A backup</d:displayname>
                            <d:getcontentlength>123</d:getcontentlength>
                            <d:getlastmodified>Mon, 18 Mar 2024 09:10:20 GMT</d:getlastmodified>
                            <d:getetag>"etag-a"</d:getetag>
                        </d:prop>
                        <d:status>HTTP/1.1 200 OK</d:status>
                    </d:propstat>
                </d:response>
            </d:multistatus>
        """.trimIndent()

        val files = WebDavXmlParser.parse(xml).sortedBy { it.path }

        assertEquals(2, files.size)
        assertEquals("/remote/a-first.backup", files[0].path)
        assertEquals("A backup", files[0].name)
        assertEquals(123L, files[0].contentLength)
        assertEquals("Mon, 18 Mar 2024 09:10:20 GMT", files[0].modifiedAt)
        assertEquals("\"etag-a\"", files[0].etag)

        assertEquals("/remote/z-last.backup", files[1].path)
        assertEquals("Z backup", files[1].name)
        assertEquals(321L, files[1].contentLength)
        assertEquals("Tue, 19 Mar 2024 10:20:30 GMT", files[1].modifiedAt)
        assertEquals("\"etag-z\"", files[1].etag)
    }

    @Test
    fun parse_fallsBackToHrefFileName_whenDisplayNameIsMissing() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <d:multistatus xmlns:d="DAV:">
                <d:response>
                    <d:href>/remote/fallback-name.backup</d:href>
                    <d:propstat>
                        <d:prop>
                            <d:getcontentlength>456</d:getcontentlength>
                        </d:prop>
                        <d:status>HTTP/1.1 200 OK</d:status>
                    </d:propstat>
                </d:response>
            </d:multistatus>
        """.trimIndent()

        val files = WebDavXmlParser.parse(xml).sortedBy { it.path }

        assertEquals(1, files.size)
        assertEquals("fallback-name.backup", files[0].name)
        assertEquals(456L, files[0].contentLength)
        assertNull(files[0].modifiedAt)
        assertNull(files[0].etag)
    }

    @Test
    fun parse_ignoresMetadata_whenOnlyNonSuccessfulPropstatExists() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <d:multistatus xmlns:d="DAV:">
                <d:response>
                    <d:href>/remote/restricted.backup</d:href>
                    <d:propstat>
                        <d:prop>
                            <d:displayname>Should not leak</d:displayname>
                            <d:getcontentlength>789</d:getcontentlength>
                            <d:getlastmodified>Wed, 20 Mar 2024 11:22:33 GMT</d:getlastmodified>
                            <d:getetag>"etag-restricted"</d:getetag>
                        </d:prop>
                        <d:status>HTTP/1.1 403 Forbidden</d:status>
                    </d:propstat>
                </d:response>
            </d:multistatus>
        """.trimIndent()

        val files = WebDavXmlParser.parse(xml).sortedBy { it.path }

        assertEquals(1, files.size)
        assertEquals("restricted.backup", files[0].name)
        assertNull(files[0].contentLength)
        assertNull(files[0].modifiedAt)
        assertNull(files[0].etag)
    }
}
