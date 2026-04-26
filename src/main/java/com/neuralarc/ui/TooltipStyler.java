package com.neuralarc.ui;

public final class TooltipStyler {
    private static final int DEFAULT_WIDTH = 260;

    private TooltipStyler() {}

    public static String text(String text) {
        return text(text, DEFAULT_WIDTH);
    }

    public static String text(String text, int width) {
        if (text == null || text.isBlank()) {
            return null;
        }
        return html(escape(text).replace("\n", "<br>"), width);
    }

    public static String html(String htmlContent) {
        return html(htmlContent, DEFAULT_WIDTH);
    }

    public static String html(String htmlContent, int width) {
        if (htmlContent == null || htmlContent.isBlank()) {
            return null;
        }
        return "<html><div style='width:" + width + "px; padding:4px 6px; font-size:10px; line-height:1.35;'>"
                + htmlContent
                + "</div></html>";
    }

    private static String escape(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
