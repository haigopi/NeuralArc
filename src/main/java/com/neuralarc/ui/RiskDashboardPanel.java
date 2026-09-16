package com.neuralarc.ui;

import com.neuralarc.analytics.LossHarvesting;
import com.neuralarc.analytics.RiskAnalytics;
import com.neuralarc.service.ReconciliationService;
import com.neuralarc.ui.chart.ChartPalette;
import com.neuralarc.ui.chart.DashboardCard;
import com.neuralarc.ui.chart.DivergingBarChart;
import com.neuralarc.ui.chart.DonutChart;
import com.neuralarc.ui.chart.HorizontalBarChart;
import com.neuralarc.ui.chart.KpiCard;
import com.neuralarc.util.FontLoader;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The chart-based Strategy Risk Dashboard body. Renders KPI tiles, exposure charts (bar + donut), an
 * open P&amp;L diverging chart, and risk advisories (possible losers / possible winners-in-losing /
 * cut-loss) from the pure {@link RiskAnalytics} outputs, plus a broker reconciliation summary.
 *
 * <p>View only — all numbers come from {@link RiskAnalytics}; this class just lays them out.
 */
final class RiskDashboardPanel extends JPanel {
    RiskDashboardPanel(String modeLabel, RiskAnalytics.Report risk, List<RiskAnalytics.PositionRisk> risks,
                       ReconciliationService.Report reconciliation, LossHarvesting.Report harvesting) {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(ChartPalette.CANVAS_BG);
        setBorder(new EmptyBorder(14, 16, 16, 16));

        add(header(modeLabel));
        add(Box.createVerticalStrut(12));
        add(row(kpiRow(risk, risks)));
        add(Box.createVerticalStrut(12));
        add(row(exposureRow(risk)));
        add(Box.createVerticalStrut(12));
        add(row(DashboardCard.of("Open P&L by Symbol", pnlChart(risks), 600, pnlChartHeight(risks))));
        add(Box.createVerticalStrut(12));
        add(sectionTitle("Risk Advisories"));
        add(Box.createVerticalStrut(6));
        add(row(advisoryRow(risks)));
        add(Box.createVerticalStrut(12));
        add(row(DashboardCard.of("Broker Reconciliation (NeuralArc vs Alpaca)", reconciliationContent(reconciliation), 600, reconciliationHeight(reconciliation))));
        add(Box.createVerticalStrut(12));
        add(sectionTitle("Loss Harvesting"));
        add(Box.createVerticalStrut(6));
        add(row(DashboardCard.of("Booking losses before the year ends", harvestingContent(harvesting),
                600, harvestingHeight(harvesting))));
        add(Box.createVerticalGlue());
    }

    // ---- Header -----------------------------------------------------------

    private JComponent header(String modeLabel) {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        JLabel title = new JLabel("Strategy Risk Dashboard");
        title.setFont(FontLoader.ui(Font.BOLD, 18f));
        title.setForeground(ChartPalette.TEXT_PRIMARY);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel sub = new JLabel(blankToDash(modeLabel) + " mode · NeuralArc strategy accounting (not Alpaca's blended view)");
        sub.setFont(FontLoader.ui(Font.PLAIN, 11f));
        sub.setForeground(ChartPalette.TEXT_MUTED);
        sub.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(title);
        panel.add(Box.createVerticalStrut(2));
        panel.add(sub);
        return leftAlignedCapped(panel);
    }

    private JComponent sectionTitle(String text) {
        JLabel label = new JLabel(text);
        label.setFont(FontLoader.ui(Font.BOLD, 13f));
        label.setForeground(ChartPalette.TEXT_PRIMARY);
        return leftAlignedCapped(label);
    }

    // ---- KPI row ----------------------------------------------------------

    private JComponent kpiRow(RiskAnalytics.Report risk, List<RiskAnalytics.PositionRisk> risks) {
        BigDecimal netPnl = BigDecimal.ZERO;
        int winners = 0;
        int losers = 0;
        for (RiskAnalytics.PositionRisk r : risks) {
            netPnl = netPnl.add(r.unrealizedPnl());
            if (r.unrealizedPnl().signum() > 0) winners++;
            else if (r.unrealizedPnl().signum() < 0) losers++;
        }
        double concentration = risk.topSymbolConcentrationPercent();

        JPanel panel = new JPanel(new GridLayout(1, 4, 12, 0));
        panel.setOpaque(false);
        panel.add(new KpiCard("Capital Allocated", money(risk.totalCapital()), ChartPalette.ACCENT,
                risk.exposureBySymbol().size() + " symbol(s)"));
        panel.add(new KpiCard("Open P&L", signedMoney(netPnl), ChartPalette.signColor(netPnl.doubleValue()),
                winners + " up · " + losers + " down"));
        panel.add(new KpiCard("Largest Loser", blankToDash(risk.largestLoserSymbol()),
                ChartPalette.signColor(risk.largestLoserPnl().doubleValue()), signedMoney(risk.largestLoserPnl())));
        panel.add(new KpiCard("Top Concentration", String.format("%.0f%%", concentration),
                concentration >= 60.0 ? ChartPalette.WARN : ChartPalette.TEXT_PRIMARY,
                concentration >= 60.0 ? "Over-concentrated" : "Diversification OK"));
        return capHeight(panel, 92);
    }

    // ---- Exposure row -----------------------------------------------------

    private JComponent exposureRow(RiskAnalytics.Report risk) {
        List<HorizontalBarChart.Bar> bars = new ArrayList<>();
        for (RiskAnalytics.Exposure e : risk.exposureBySymbol()) {
            bars.add(new HorizontalBarChart.Bar(e.key(), e.value().doubleValue(),
                    money(e.value()) + "  (" + String.format("%.1f%%", e.percentOfTotal()) + ")", ChartPalette.ACCENT));
        }
        HorizontalBarChart bySymbol = new HorizontalBarChart(bars);

        List<DonutChart.Slice> slices = new ArrayList<>();
        for (RiskAnalytics.Exposure e : risk.exposureByWorkspace()) {
            slices.add(new DonutChart.Slice(e.key(), e.value().doubleValue(),
                    money(e.value()) + " · " + String.format("%.1f%%", e.percentOfTotal())));
        }
        DonutChart byWorkspace = new DonutChart(slices);

        int height = Math.max(160, Math.max(bySymbol.getPreferredSize().height, byWorkspace.getPreferredSize().height) + 52);
        JPanel panel = new JPanel(new GridLayout(1, 2, 12, 0));
        panel.setOpaque(false);
        panel.add(DashboardCard.of("Exposure by Symbol", bySymbol, 420, height));
        panel.add(DashboardCard.of("Exposure by Strategy", byWorkspace, 420, height));
        return capHeight(panel, height);
    }

    // ---- Open P&L diverging chart ----------------------------------------

    private DivergingBarChart pnlChart(List<RiskAnalytics.PositionRisk> risks) {
        Map<String, BigDecimal> pnlBySymbol = new LinkedHashMap<>();
        for (RiskAnalytics.PositionRisk r : risks) {
            pnlBySymbol.merge(r.symbol(), r.unrealizedPnl(), BigDecimal::add);
        }
        List<DivergingBarChart.Bar> bars = new ArrayList<>();
        pnlBySymbol.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .forEach(e -> bars.add(new DivergingBarChart.Bar(e.getKey(), e.getValue().doubleValue(), signedMoney(e.getValue()))));
        return new DivergingBarChart(bars);
    }

    private int pnlChartHeight(List<RiskAnalytics.PositionRisk> risks) {
        long symbols = risks.stream().map(RiskAnalytics.PositionRisk::symbol).distinct().count();
        return (int) Math.max(64, symbols * 26 + 6) + 44;
    }

    // ---- Advisories -------------------------------------------------------

    private JComponent advisoryRow(List<RiskAnalytics.PositionRisk> risks) {
        List<RiskAnalytics.PositionRisk> protect = byVerdict(risks, RiskAnalytics.RiskVerdict.PROTECT_GAINS);
        List<RiskAnalytics.PositionRisk> wait = byVerdict(risks, RiskAnalytics.RiskVerdict.WAIT_TO_BOOK_LOSS);
        List<RiskAnalytics.PositionRisk> cut = byVerdict(risks, RiskAnalytics.RiskVerdict.CUT_LOSS);

        int height = Math.max(140, 44 + maxRows(protect, wait, cut) * 52);
        JPanel panel = new JPanel(new GridLayout(1, 3, 12, 0));
        panel.setOpaque(false);
        panel.add(DashboardCard.of("Possible Losers — Protect Gains", advisoryList(protect,
                "No at-risk winners. Gains have a healthy cushion."), 320, height));
        panel.add(DashboardCard.of("Possible Winners in Losing — Wait to Book Losses", advisoryList(wait,
                "No recoverable losers right now."), 320, height));
        panel.add(DashboardCard.of("Cut-Loss Candidates", advisoryList(cut,
                "No positions have breached their stop."), 320, height));
        return capHeight(panel, height);
    }

    private JComponent advisoryList(List<RiskAnalytics.PositionRisk> rows, String emptyText) {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        if (rows.isEmpty()) {
            JLabel empty = new JLabel("<html><div style='width:280px;color:#6c7684;'>" + escape(emptyText) + "</div></html>");
            empty.setFont(FontLoader.ui(Font.PLAIN, 11f));
            empty.setAlignmentX(Component.LEFT_ALIGNMENT);
            panel.add(empty);
            return panel;
        }
        boolean first = true;
        for (RiskAnalytics.PositionRisk r : rows) {
            if (!first) {
                panel.add(Box.createVerticalStrut(8));
            }
            first = false;
            panel.add(advisoryEntry(r));
        }
        return panel;
    }

    private JComponent advisoryEntry(RiskAnalytics.PositionRisk r) {
        JPanel entry = new JPanel();
        entry.setOpaque(false);
        entry.setLayout(new BoxLayout(entry, BoxLayout.Y_AXIS));
        entry.setAlignmentX(Component.LEFT_ALIGNMENT);

        Color pnlColor = ChartPalette.signColor(r.unrealizedPnl().doubleValue());
        String hex = String.format("#%02x%02x%02x", pnlColor.getRed(), pnlColor.getGreen(), pnlColor.getBlue());
        JLabel head = new JLabel("<html><b>" + escape(r.symbol()) + "</b>"
                + " &nbsp;<span style='color:" + hex + ";'>" + escape(signedMoney(r.unrealizedPnl()))
                + " (" + String.format("%+.1f%%", r.unrealizedPercent()) + ")</span></html>");
        head.setFont(FontLoader.ui(Font.BOLD, 11f));
        head.setForeground(ChartPalette.TEXT_PRIMARY);
        head.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel advice = new JLabel("<html><div style='width:280px;color:#52606d;'>" + escape(r.advice()) + "</div></html>");
        advice.setFont(FontLoader.ui(Font.PLAIN, 10f));
        advice.setAlignmentX(Component.LEFT_ALIGNMENT);

        entry.add(head);
        entry.add(advice);
        return entry;
    }

    // ---- Reconciliation ---------------------------------------------------

    private JComponent reconciliationContent(ReconciliationService.Report reconciliation) {
        String body;
        if (reconciliation == null || reconciliation.lines().isEmpty()) {
            body = "No tracked positions to reconcile.";
        } else if (!reconciliation.hasMismatches()) {
            body = "All " + reconciliation.lines().size() + " symbol(s) reconcile with the broker.";
        } else {
            StringBuilder sb = new StringBuilder();
            sb.append("<span style='color:#c93a3a;'><b>").append(reconciliation.mismatchCount())
                    .append(" mismatch(es):</b></span><br>");
            for (ReconciliationService.Line line : reconciliation.lines()) {
                if (line.status() == ReconciliationService.Status.MATCH) {
                    continue;
                }
                sb.append("&nbsp;&nbsp;• <b>").append(escape(line.symbol())).append("</b>: ")
                        .append(escape(describe(line))).append("<br>");
            }
            sb.append("<br><i>NeuralArc never auto-corrects — review the broker account manually.</i>");
            body = sb.toString();
        }
        JLabel label = new JLabel("<html><div style='width:560px;color:#52606d;'>" + body + "</div></html>");
        label.setFont(FontLoader.ui(Font.PLAIN, 11f));
        label.setVerticalAlignment(JLabel.TOP);
        return label;
    }

    private int reconciliationHeight(ReconciliationService.Report reconciliation) {
        int mismatches = reconciliation == null ? 0 : (int) reconciliation.lines().stream()
                .filter(l -> l.status() != ReconciliationService.Status.MATCH).count();
        return 64 + Math.max(0, mismatches) * 18;
    }

    private String describe(ReconciliationService.Line line) {
        return switch (line.status()) {
            case QTY_MISMATCH -> "qty local " + plain(line.localQuantity()) + " vs broker " + plain(line.brokerQuantity());
            case COST_MISMATCH -> "avg cost local " + money(line.localAverageCost()) + " vs broker " + money(line.brokerAverageCost());
            case MISSING_LOCAL -> "held at broker (" + plain(line.brokerQuantity()) + ") but no local strategy";
            case MISSING_BROKER -> "tracked locally (" + plain(line.localQuantity()) + ") but absent at broker";
            case MATCH -> "matches";
        };
    }

    // ---- Loss harvesting --------------------------------------------------

    /**
     * What booking the open losses would do to this year's tax bill, drawn from the account's own
     * trades. Every colour is set explicitly: this card is white while the app runs dark, so text left
     * to the theme's own foreground is unreadable here. Deliberately worded as information — the last
     * line says it is not tax advice.
     */
    private JComponent harvestingContent(LossHarvesting.Report harvesting) {
        if (harvesting == null) {
            return capHeight(new JLabel("<html><div style='width:560px;color:" + hex(ChartPalette.TEXT_MUTED) + ";'>"
                    + "Loss harvesting could not be worked out for this view.</div></html>"), 40);
        }
        String primary = hex(ChartPalette.TEXT_PRIMARY);
        StringBuilder html = new StringBuilder("<html><div style='width:560px;'>")
                .append("<table width='100%' cellpadding='6' cellspacing='0'>")
                .append("<tr><td bgcolor='#FBF0D2'><font color='#4A3A12' size='4'><b>")
                .append(escape(harvesting.headline())).append("</b></font></td></tr>")
                .append("<tr><td bgcolor='#E6EEFA'><font color='#1C3A63'><b>Sell by ")
                .append(escape(LossHarvesting.date(harvesting.sellBy()))).append("</b> for the loss to count in ")
                .append(harvesting.sellBy().getYear()).append(", and do not buy the same symbol back before <b>")
                .append(escape(LossHarvesting.date(harvesting.repurchaseAllowedFrom())))
                .append("</b> or the loss is disallowed.</font></td></tr></table>");
        if (harvesting.hasCandidates()) {
            html.append("<table cellpadding='3' cellspacing='0' style='margin-top:8px;'>");
            html.append(harvestRows("Worth booking this year", harvesting.recommended()));
            html.append(harvestRows("Only builds a carry-forward", harvesting.optional()));
            html.append("</table>");
        }
        html.append("<div style='margin-top:8px;'>");
        for (String note : harvesting.notes()) {
            html.append("<font color='").append(hex(ChartPalette.TEXT_MUTED)).append("'>&bull; ")
                    .append(escape(note)).append("</font><br>");
        }
        html.append("</div></div></html>");
        JLabel label = new JLabel(html.toString());
        label.setFont(FontLoader.ui(Font.PLAIN, 11f));
        label.setForeground(ChartPalette.TEXT_PRIMARY);
        label.setVerticalAlignment(JLabel.TOP);
        return leftAlignedCapped(label);
    }

    private String harvestRows(String heading, List<LossHarvesting.Lot> lots) {
        if (lots.isEmpty()) {
            return "";
        }
        StringBuilder rows = new StringBuilder("<tr><td colspan='5' style='padding-top:8px;'><font color='")
                .append(hex(ChartPalette.TEXT_PRIMARY)).append("'><b>").append(escape(heading)).append("</b></font></td></tr>");
        for (LossHarvesting.Lot lot : lots) {
            String termLabel = switch (lot.term()) {
                case SHORT -> "short-term";
                case LONG -> "long-term";
                case UNKNOWN -> "term unknown";
            };
            rows.append("<tr>")
                    .append("<td><font color='").append(hex(ChartPalette.TEXT_PRIMARY)).append("'><b>")
                    .append(escape(lot.symbol())).append("</b></font></td>")
                    .append("<td align='right'><font color='").append(hex(ChartPalette.NEGATIVE)).append("'><b>")
                    .append(escape(money(lot.loss()))).append("</b></font></td>")
                    .append("<td><font color='").append(hex(ChartPalette.TEXT_MUTED)).append("'>")
                    .append(escape(termLabel)).append("</font></td>")
                    .append("<td><font color='").append(hex(ChartPalette.TEXT_PRIMARY)).append("'>")
                    .append(escape(lot.action())).append("</font></td>")
                    .append("<td><font color='").append(hex(ChartPalette.WARN)).append("'>")
                    .append(lot.washSaleRisk() ? "buys back automatically" : "").append("</font></td>")
                    .append("</tr>");
        }
        return rows.toString();
    }

    private int harvestingHeight(LossHarvesting.Report harvesting) {
        int lots = harvesting == null ? 0 : harvesting.recommended().size() + harvesting.optional().size();
        int notes = harvesting == null ? 0 : harvesting.notes().size();
        return 120 + lots * 22 + notes * 30;
    }

    private String hex(Color color) {
        return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
    }

    // ---- Layout helpers ---------------------------------------------------

    /** Left-align a section and stop BoxLayout from stretching it vertically beyond its preferred height. */
    private JComponent row(JComponent component) {
        component.setAlignmentX(Component.LEFT_ALIGNMENT);
        return component;
    }

    private JComponent leftAlignedCapped(JComponent component) {
        component.setAlignmentX(Component.LEFT_ALIGNMENT);
        return capHeight(component, component.getPreferredSize().height);
    }

    private JComponent capHeight(JComponent component, int height) {
        component.setAlignmentX(Component.LEFT_ALIGNMENT);
        component.setMaximumSize(new Dimension(Integer.MAX_VALUE, height + 30));
        return component;
    }

    private List<RiskAnalytics.PositionRisk> byVerdict(List<RiskAnalytics.PositionRisk> risks, RiskAnalytics.RiskVerdict verdict) {
        List<RiskAnalytics.PositionRisk> out = new ArrayList<>();
        for (RiskAnalytics.PositionRisk r : risks) {
            if (r.verdict() == verdict) {
                out.add(r);
            }
        }
        out.sort((a, b) -> b.unrealizedPnl().abs().compareTo(a.unrealizedPnl().abs()));
        return out;
    }

    @SafeVarargs
    private final int maxRows(List<RiskAnalytics.PositionRisk>... lists) {
        int max = 1;
        for (List<RiskAnalytics.PositionRisk> list : lists) {
            max = Math.max(max, Math.max(1, list.size()));
        }
        return max;
    }

    // ---- Formatting -------------------------------------------------------

    private String money(BigDecimal value) {
        BigDecimal v = value == null ? BigDecimal.ZERO : value;
        return v.signum() < 0 ? "-$" + v.abs().toPlainString() : "$" + v.toPlainString();
    }

    private String signedMoney(BigDecimal value) {
        BigDecimal v = value == null ? BigDecimal.ZERO : value;
        if (v.signum() < 0) return "-$" + v.abs().toPlainString();
        if (v.signum() > 0) return "+$" + v.toPlainString();
        return "$" + v.toPlainString();
    }

    private String plain(BigDecimal value) {
        return value == null ? "0" : value.stripTrailingZeros().toPlainString();
    }

    private String blankToDash(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }

    private String escape(String value) {
        return value == null ? "" : value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
