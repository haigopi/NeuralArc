package com.neuralarc.ui;

import com.neuralarc.model.ScanHistoryEntry;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.Component;
import java.awt.GridBagLayout;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Compact, read-only "recent scan history" table shown inside a strategy's empty state. Renders the
 * most recent scan runs (when they ran, whether they were manual or scheduled, and a short result
 * summary) using the same muted, transparent styling as the empty-state guidance panels so it reads
 * as secondary context below the Analyze call-to-action. Hidden entirely when there is no history
 * yet, so a first-time strategy shows only its description and button.
 */
final class ScanHistoryTablePanel extends JPanel {
    static final int MAX_ROWS = 10;
    private static final DateTimeFormatter WHEN_FORMAT =
            DateTimeFormatter.ofPattern("MMM d, h:mm a").withZone(ZoneId.systemDefault());

    private final JLabel content = new JLabel();

    ScanHistoryTablePanel() {
        super(new GridBagLayout());
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(14, 8, 4, 8));
        content.setAlignmentX(Component.CENTER_ALIGNMENT);
        add(content);
        setEntries(List.of());
    }

    /** Replace the rendered rows; hides the panel when {@code entries} is empty. */
    void setEntries(List<ScanHistoryEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            content.setText("");
            setVisible(false);
            return;
        }
        content.setText(buildHtml(entries));
        setVisible(true);
    }

    private static String buildHtml(List<ScanHistoryEntry> entries) {
        StringBuilder html = new StringBuilder("<html><div style='text-align:left; width:420px;'>");
        html.append("<div style='font-size:9px; color:#ffffff;'><b>Recent scan history (last ")
                .append(MAX_ROWS).append(")</b></div>");
        html.append("<table style='margin-top:6px; width:100%; font-size:9px; color:#ffccff;' cellspacing='0' cellpadding='2'>");
        html.append("<tr style='color:#ffffff;'>")
                .append("<td><b>When</b></td>")
                .append("<td><b>Trigger</b></td>")
                .append("<td><b>Result</b></td></tr>");
        int rows = Math.min(MAX_ROWS, entries.size());
        for (int i = 0; i < rows; i++) {
            ScanHistoryEntry entry = entries.get(i);
            html.append("<tr>")
                    .append("<td valign='top'>").append(escape(WHEN_FORMAT.format(entry.ranAt()))).append("</td>")
                    .append("<td valign='top'>").append(escape(entry.trigger())).append("</td>")
                    .append("<td valign='top'>").append(escape(entry.summary())).append("</td>")
                    .append("</tr>");
        }
        html.append("</table></div></html>");
        return html.toString();
    }

    private static String escape(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
