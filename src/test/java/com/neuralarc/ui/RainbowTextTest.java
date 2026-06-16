package com.neuralarc.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RainbowTextTest {

    @Test
    void wrapsEachVisibleCharWithACycledColor() {
        String html = RainbowText.toHtml("PL");
        assertTrue(html.startsWith("<html>"));
        assertTrue(html.endsWith("</html>"));
        // Two visible chars → two font tags with different colors (rainbow cycling).
        assertEquals(2, countOccurrences(html, "<font color='#"));
        assertTrue(html.contains(">P</font>"));
        assertTrue(html.contains(">L</font>"));
    }

    @Test
    void escapesHtmlSpecialCharactersAndKeepsSpaces() {
        String html = RainbowText.toHtml("P&L > $1");
        assertTrue(html.contains("&amp;"));
        assertTrue(html.contains("&gt;"));
        assertTrue(html.contains("&nbsp;"));
        // Raw, unescaped specials must not leak into the markup.
        assertFalse(html.contains(">&<"));
    }

    @Test
    void emptyOrNullReturnsEmpty() {
        assertEquals("", RainbowText.toHtml(""));
        assertEquals("", RainbowText.toHtml(null));
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        int idx = 0;
        while ((idx = haystack.indexOf(needle, idx)) >= 0) {
            count++;
            idx += needle.length();
        }
        return count;
    }
}
