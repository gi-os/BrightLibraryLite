package com.gios.brightlibrarylite

import com.gios.brightlibrarylite.parser.BookParser
import com.gios.brightlibrarylite.parser.HtmlStripper
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The EPUB parser is the full app's, ported unchanged apart from its debug logging. These pin the
 * parts the Lite tool leans on when it imports a file from the Books folder: title and author from
 * the OPF, text in spine order, chapters from the NCX, and the cover image.
 */
class EpubParserTest {

    private fun epub(vararg entries: Pair<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            for ((name, bytes) in entries) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private val container = """<?xml version="1.0"?>
<container><rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles></container>"""

    private val opf = """<?xml version="1.0"?>
<package><metadata>
<dc:title>A Small Book</dc:title><dc:creator>Jane Writer</dc:creator>
<meta name="cover" content="cov"/>
</metadata><manifest>
<item id="c1" href="text/one.xhtml" media-type="application/xhtml+xml"/>
<item id="c2" href="text/two.xhtml" media-type="application/xhtml+xml"/>
<item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>
<item id="cov" href="images/cover.jpg" media-type="image/jpeg"/>
</manifest><spine><itemref idref="c1"/><itemref idref="c2"/></spine></package>"""

    private val ncx = """<?xml version="1.0"?>
<ncx><navMap>
<navPoint id="n1"><navLabel><text>Opening</text></navLabel><content src="text/one.xhtml"/></navPoint>
<navPoint id="n2"><navLabel><text>Closing</text></navLabel><content src="text/two.xhtml#top"/></navPoint>
</navMap></ncx>"""

    private val cover = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 1, 2, 3)

    private fun book() = epub(
        "mimetype" to "application/epub+zip".toByteArray(),
        "META-INF/container.xml" to container.toByteArray(),
        "OEBPS/content.opf" to opf.toByteArray(),
        "OEBPS/toc.ncx" to ncx.toByteArray(),
        "OEBPS/text/one.xhtml" to "<html><body><h1>Opening</h1><p>First words here.</p></body></html>".toByteArray(),
        "OEBPS/text/two.xhtml" to "<html><body><h1>Closing</h1><p>Last <i>words</i> there.</p></body></html>".toByteArray(),
        "OEBPS/images/cover.jpg" to cover,
    )

    @Test
    fun `reads metadata, spine text, chapters and cover`() {
        val parsed = BookParser.parseBytes(book(), "small.epub")
        assertEquals("A Small Book", parsed.title)
        assertEquals("Jane Writer", parsed.author)
        assertEquals("EPUB", parsed.format)
        val plain = HtmlStripper.stripItalicMarkers(parsed.text)
        assertTrue(plain.indexOf("First words") < plain.indexOf("Last"), "spine order")
        assertEquals(listOf("Opening", "Closing"), parsed.chapters.map { it.title })
        assertEquals(0, parsed.chapters.first().charOffset)
        assertTrue(parsed.chapters[1].charOffset > 0)
        val img = assertNotNull(parsed.coverImage)
        assertTrue(img.contentEquals(cover))
    }

    @Test
    fun `falls back to the file name when the OPF has no title`() {
        val bare = epub(
            "META-INF/container.xml" to container.toByteArray(),
            "OEBPS/content.opf" to "<package><manifest><item id=\"c1\" href=\"a.xhtml\"/></manifest><spine><itemref idref=\"c1\"/></spine></package>".toByteArray(),
            "OEBPS/a.xhtml" to "<p>Hello there.</p>".toByteArray(),
        )
        val parsed = BookParser.parseBytes(bare, "my_book.epub")
        assertEquals("my_book", parsed.title)
    }

    @Test
    fun `refuses formats it does not read`() {
        assertFailsWith<IllegalArgumentException> { BookParser.parseBytes(ByteArray(4), "notes.pdf") }
    }
}
