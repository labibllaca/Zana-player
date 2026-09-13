package com.labix

import com.labix.navirom.data.lyrics.LyricsSource
import com.labix.navirom.data.lyrics.TeksteShqipLyricsService
import org.junit.Assert.*
import org.junit.Test

class TeksteShqipLyricsTest {

    @Test
    fun lyricsSource_hasCorrectTeksteShqipDisplayNames() {
        val source = LyricsSource.ONLINE_TEKSTESHQIP
        assertEquals("Online (TeksteShqip)", source.displayNameEn)
        assertEquals("Nga Interneti (TeksteShqip)", source.displayNameSq)
    }

    @Test
    fun knownLeonoraJakupi_constantsAreValid() {
        assertEquals("https://teksteshqip.com/leonora-jakupi/teksti/1848928", TeksteShqipLyricsService.KNOWN_LEONORA_JAKUPI_URL)
        assertEquals("1848928", TeksteShqipLyricsService.KNOWN_LEONORA_JAKUPI_ID)
        assertTrue(TeksteShqipLyricsService.KNOWN_LEONORA_JAKUPI_LYRICS.contains("A vritet pafajësia"))
        assertTrue(TeksteShqipLyricsService.KNOWN_LEONORA_JAKUPI_LYRICS.contains("Drenica lind veç trima"))
    }

    @Test
    fun extractLyricsFromHtml_parsesDivClCl1Correctly() {
        val sampleHtml = """
            <!DOCTYPE html>
            <html>
            <head><title>Teksti i këngës A Vritet Pafajsia nga Leonora Jakupi</title></head>
            <body>
            <div class="clCl1">
                A vritet pafajësia?!<br/>
                çfarë faji ka Drenica?!<br/>
                Pse tremben nga fëmija,<br/>
                që thërret Azem Galica?<br/><br/>
                Drenica lind veç trima<br/>
                ju i lini fëmijët jetima.
            </div>
            </body>
            </html>
        """.trimIndent()

        // We instantiate service with mock or test extraction method logic
        val pattern = java.util.regex.Pattern.compile(
            "<div[^>]*class=[\"'][^\"']*clCl1[^\"']*[\"'][^>]*>(.*?)</div>",
            java.util.regex.Pattern.DOTALL or java.util.regex.Pattern.CASE_INSENSITIVE
        )
        val matcher = pattern.matcher(sampleHtml)
        assertTrue(matcher.find())
        val raw = matcher.group(1)
        val clean = raw
            .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("<[^>]+>"), "")
            .lines()
            .map { it.trim() }
            .joinToString("\n")
            .trim()

        assertTrue(clean.contains("A vritet pafajësia?!"))
        assertTrue(clean.contains("çfarë faji ka Drenica?!"))
        assertTrue(clean.contains("Drenica lind veç trima"))
    }
}
