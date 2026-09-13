package com.neuralarc.service;

import com.neuralarc.analytics.PortfolioSnapshot;
import com.neuralarc.analytics.RiskAnalytics;
import com.neuralarc.util.Monetary;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Writes the portfolio snapshot email: the totals across every workspace (the bottom status bar's
 * figures), each workspace's figures, the Risk Dashboard's analysis — its KPIs, open P&amp;L by
 * symbol, exposure and risk advisories — and the broker reconciliation with Alpaca. The HTML uses tables and inline styles only, and draws bars
 * as sized cells rather than images, so it reads the same in web, desktop and phone mail clients.
 */
public final class PortfolioSnapshotEmailBuilder {
    static final String POSITIVE = "#1E8E5A";
    static final String NEGATIVE = "#C93A3A";
    static final int MAX_EXPOSURE_ROWS = 12;
    private static final String TEXT = "#111827";
    private static final String MUTED = "#6B7280";
    private static final String BORDER = "#E5E7EB";
    private static final String SOFT = "#F9FAFB";
    private static final String ACCENT = "#3B6FD8";
    private static final String WARN = "#B7791F";
    private static final String HEADER = "#1F2A44";
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy 'at' h:mm a 'ET'", Locale.US);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEE MMM d", Locale.US);

    private record Card(String label, String value, String color, String note) {
    }

    public String subject(PortfolioSnapshot snapshot) {
        return "NeuralArc: " + snapshot.modeLabel() + " - Portfolio snapshot: " + snapshot.occasion() + ", "
                + DAY.format(snapshot.takenAt()) + " - Open P&L " + signedMoney(snapshot.allWorkspaces().unrealized());
    }

    public String text(PortfolioSnapshot snapshot) {
        PortfolioSnapshot.Figures all = snapshot.allWorkspaces();
        StringBuilder text = new StringBuilder()
                .append("NeuralArc portfolio snapshot (").append(snapshot.modeLabel()).append(")\n")
                .append(snapshot.occasion()).append(", ").append(WHEN.format(snapshot.takenAt())).append("\n\n")
                .append("ALL WORKSPACES\n")
                .append("Market value: ").append(money(all.marketValue())).append('\n')
                .append("Invested vs upcoming buys: ").append(money(all.invested())).append(" vs ")
                .append(money(all.upcomingBuys())).append(" (total ").append(money(all.committed())).append(")\n")
                .append("Funds available: ").append(snapshot.fundsAvailable()).append('\n')
                .append("Open P&L: ").append(signedMoney(all.unrealized()))
                .append("   Realized: ").append(signedMoney(all.realized()))
                .append("   Today: ").append(signedMoney(all.today()))
                .append("   Total: ").append(signedMoney(all.total())).append('\n')
                .append("Gaining: ").append(all.gainingCount()).append(" (").append(signedMoney(all.gainingPnl())).append(")")
                .append("   Losing: ").append(all.losingCount()).append(" (").append(signedMoney(all.losingPnl())).append(")\n")
                .append("Pending buy: ").append(all.pendingBuyPositions())
                .append("   Pending sell: ").append(all.pendingSellPositions())
                .append("   Win rate: ").append(winText(all)).append('\n');
        if (!snapshot.workspaces().isEmpty()) {
            text.append("\nBY WORKSPACE\n");
            for (PortfolioSnapshot.WorkspaceRow row : snapshot.workspaces()) {
                PortfolioSnapshot.Figures f = row.figures();
                text.append("- ").append(row.name()).append(": invested ").append(money(f.invested()))
                        .append(", upcoming ").append(money(f.upcomingBuys()))
                        .append(", open P&L ").append(signedMoney(f.unrealized()))
                        .append(", realized ").append(signedMoney(f.realized()))
                        .append(", today ").append(signedMoney(f.today()))
                        .append(", up ").append(f.gainingCount()).append(" / down ").append(f.losingCount())
                        .append(", pending buy ").append(f.pendingBuyPositions())
                        .append(" / sell ").append(f.pendingSellPositions()).append('\n');
            }
        }
        if (snapshot.risk() != null) {
            RiskAnalytics.Report risk = snapshot.risk();
            text.append("\nRISK ANALYSIS\n")
                    .append("Capital allocated: ").append(money(risk.totalCapital()))
                    .append("   Largest loser: ").append(dash(risk.largestLoserSymbol()))
                    .append(" (").append(signedMoney(risk.largestLoserPnl())).append(")")
                    .append("   Top concentration: ").append(String.format(Locale.US, "%.0f%%", risk.topSymbolConcentrationPercent()))
                    .append('\n').append("Open P&L by symbol:\n");
            for (Map.Entry<String, BigDecimal> entry : pnlBySymbol(snapshot.positionRisks())) {
                text.append("  ").append(entry.getKey()).append(' ').append(signedMoney(entry.getValue())).append('\n');
            }
            for (RiskAnalytics.RiskVerdict verdict : List.of(RiskAnalytics.RiskVerdict.PROTECT_GAINS,
                    RiskAnalytics.RiskVerdict.WAIT_TO_BOOK_LOSS, RiskAnalytics.RiskVerdict.CUT_LOSS)) {
                List<RiskAnalytics.PositionRisk> group = byVerdict(snapshot.positionRisks(), verdict);
                if (!group.isEmpty()) {
                    text.append(advisoryTitle(verdict)).append(":\n");
                    for (RiskAnalytics.PositionRisk risk1 : group) {
                        text.append("  ").append(risk1.symbol()).append(' ').append(signedMoney(risk1.unrealizedPnl()))
                                .append(" - ").append(risk1.advice()).append('\n');
                    }
                }
            }
        }
        if (snapshot.brokerCheck() != null) {
            text.append("\nBROKER RECONCILIATION (NeuralArc vs Alpaca)\n").append(brokerSummary(snapshot.brokerCheck())).append('\n');
            for (PortfolioSnapshot.Mismatch mismatch : snapshot.brokerCheck().mismatches()) {
                text.append("  ").append(mismatch.symbol()).append(": ").append(mismatch.description()).append('\n');
            }
        }
        return text.append('\n').append(footer(snapshot)).toString();
    }

    public String html(PortfolioSnapshot snapshot) {
        StringBuilder body = new StringBuilder()
                .append(section("All workspaces", "The figures in NeuralArc's bottom status bar, across every workspace.",
                        allWorkspaceCards(snapshot)));
        if (!snapshot.workspaces().isEmpty()) {
            body.append(section("By workspace", "Each workspace's figures, as shown under its grid.",
                    workspaceTable(snapshot.workspaces())));
        }
        body.append(riskSection(snapshot)).append(brokerSection(snapshot.brokerCheck()));
        return "<!doctype html><html><head><meta charset=\"utf-8\"></head>"
                + "<body style=\"margin:0;background:#f4f7fb;color:#172033;font-family:Arial,Helvetica,sans-serif;\">"
                + "<div style=\"max-width:760px;margin:0 auto;padding:24px;\">"
                + "<div style=\"background:" + HEADER + ";color:#ffffff;border-radius:16px 16px 0 0;padding:22px 24px;\">"
                + "<div style=\"font-size:12px;letter-spacing:1.4px;text-transform:uppercase;color:#E5E7EB;\">NeuralArc &middot; "
                + escape(snapshot.modeLabel()) + "</div>"
                + "<h1 style=\"margin:8px 0 4px;font-size:24px;line-height:1.25;\">Portfolio snapshot</h1>"
                + "<div style=\"font-size:14px;color:#F3F4F6;\">" + escape(snapshot.occasion()) + " &middot; "
                + escape(WHEN.format(snapshot.takenAt())) + "</div>"
                + "</div>"
                + "<div style=\"background:#ffffff;border:1px solid " + BORDER + ";border-top:0;border-radius:0 0 16px 16px;padding:6px 24px 22px;\">"
                + body
                + "<p style=\"margin:22px 0 0;color:" + MUTED + ";font-size:12px;line-height:1.5;\">" + escape(footer(snapshot)) + "</p>"
                + "</div></div></body></html>";
    }

    // ---- Totals ------------------------------------------------------------

    private String allWorkspaceCards(PortfolioSnapshot snapshot) {
        PortfolioSnapshot.Figures f = snapshot.allWorkspaces();
        return cards(List.of(
                new Card("Market value", money(f.marketValue()), TEXT, "Shares held, at the latest price"),
                new Card("Invested", money(f.invested()), TEXT, "Paid for the shares held"),
                new Card("Upcoming buys", money(f.upcomingBuys()), TEXT, positions(f.pendingBuyPositions()) + " waiting on a buy"),
                new Card("Invested + upcoming", money(f.committed()), TEXT, "Committed once those buys fill"),
                new Card("Funds available", snapshot.fundsAvailable(), TEXT, "Buying power at the broker"),
                new Card("Open P&L", signedMoney(f.unrealized()), colorOf(f.unrealized()),
                        f.gainingCount() + " up · " + f.losingCount() + " down"),
                new Card("Realized", signedMoney(f.realized()), colorOf(f.realized()), "Locked in by sells"),
                new Card("Today", signedMoney(f.today()), colorOf(f.today()), "Locked in by today's sells"),
                new Card("Total P&L", signedMoney(f.total()), colorOf(f.total()), "Realized plus open"),
                new Card("Win rate", f.sells() == 0 ? "-" : String.format(Locale.US, "%.0f%%", f.winRatePercent()), TEXT,
                        f.sells() == 0 ? "No sells yet" : "of " + f.sells() + (f.sells() == 1 ? " sell" : " sells")),
                new Card("Gaining", String.valueOf(f.gainingCount()), POSITIVE, signedMoney(f.gainingPnl()) + " above cost"),
                new Card("Losing", String.valueOf(f.losingCount()), NEGATIVE, signedMoney(f.losingPnl()) + " below cost"),
                new Card("Pending buy", String.valueOf(f.pendingBuyPositions()), TEXT, "Positions waiting on a buy"),
                new Card("Pending sell", String.valueOf(f.pendingSellPositions()), TEXT, "Positions waiting on a sell"),
                new Card("Open positions", String.valueOf(f.openPositions()), TEXT, "Holding shares now")));
    }

    private String workspaceTable(List<PortfolioSnapshot.WorkspaceRow> rows) {
        StringBuilder table = new StringBuilder("<table cellpadding=\"0\" cellspacing=\"0\" style=\"width:100%;border-collapse:collapse;border:1px solid ")
                .append(BORDER).append(";font-size:12px;\"><tr>");
        String[] headings = {"Workspace", "Invested", "Upcoming", "Open P&L", "Realized", "Today", "Up / down", "Pending buy / sell"};
        for (String heading : headings) {
            table.append("<th style=\"text-align:").append("Workspace".equals(heading) ? "left" : "right")
                    .append(";padding:8px 10px;background:").append(SOFT).append(";border-bottom:1px solid ").append(BORDER)
                    .append(";color:#374151;font-weight:700;\">").append(escape(heading)).append("</th>");
        }
        table.append("</tr>");
        for (PortfolioSnapshot.WorkspaceRow row : rows) {
            PortfolioSnapshot.Figures f = row.figures();
            table.append("<tr>")
                    .append(cell(escape(row.name()), "left", TEXT, true))
                    .append(cell(escape(money(f.invested())), "right", TEXT, false))
                    .append(cell(escape(money(f.upcomingBuys())), "right", TEXT, false))
                    .append(cell(escape(signedMoney(f.unrealized())), "right", colorOf(f.unrealized()), true))
                    .append(cell(escape(signedMoney(f.realized())), "right", colorOf(f.realized()), false))
                    .append(cell(escape(signedMoney(f.today())), "right", colorOf(f.today()), false))
                    .append(cell("<span style=\"color:" + POSITIVE + ";\">" + f.gainingCount() + "</span> / <span style=\"color:"
                            + NEGATIVE + ";\">" + f.losingCount() + "</span>", "right", TEXT, true))
                    .append(cell(f.pendingBuyPositions() + " / " + f.pendingSellPositions(), "right", TEXT, false))
                    .append("</tr>");
        }
        return table.append("</table>").toString();
    }

    // ---- Risk analysis -----------------------------------------------------

    private String riskSection(PortfolioSnapshot snapshot) {
        RiskAnalytics.Report risk = snapshot.risk();
        if (risk == null) {
            return "";
        }
        List<RiskAnalytics.PositionRisk> risks = snapshot.positionRisks();
        return section("Risk analysis", "The Risk Dashboard's view of the positions held now.", riskCards(risk, risks))
                + subsection("Open P&L by symbol", "Unrealized profit or loss of each symbol held, largest gain first.",
                        divergingBars(risks))
                + subsection("Exposure by symbol", "How much of the money in the market sits in each symbol.",
                        exposureBars(risk.exposureBySymbol()))
                + subsection("Exposure by workspace", "How much sits in each workspace.",
                        exposureBars(risk.exposureByWorkspace()))
                + subsection("Risk advisories", "Positions that may need attention, grouped by what their plan suggests.",
                        advisory(risks, RiskAnalytics.RiskVerdict.PROTECT_GAINS, "No at-risk winners. Gains have a healthy cushion.")
                                + advisory(risks, RiskAnalytics.RiskVerdict.WAIT_TO_BOOK_LOSS, "No recoverable losers right now.")
                                + advisory(risks, RiskAnalytics.RiskVerdict.CUT_LOSS, "No positions have breached their stop."));
    }

    private String riskCards(RiskAnalytics.Report risk, List<RiskAnalytics.PositionRisk> risks) {
        BigDecimal net = BigDecimal.ZERO;
        int up = 0;
        int down = 0;
        for (RiskAnalytics.PositionRisk position : risks) {
            net = net.add(position.unrealizedPnl());
            if (position.unrealizedPnl().signum() > 0) {
                up++;
            } else if (position.unrealizedPnl().signum() < 0) {
                down++;
            }
        }
        double concentration = risk.topSymbolConcentrationPercent();
        boolean overConcentrated = concentration >= 60.0;
        return cards(List.of(
                new Card("Capital allocated", money(risk.totalCapital()), TEXT, risk.exposureBySymbol().size() + " symbol(s)"),
                new Card("Open P&L", signedMoney(net), colorOf(net), up + " up · " + down + " down"),
                new Card("Largest loser", dash(risk.largestLoserSymbol()), colorOf(risk.largestLoserPnl()),
                        signedMoney(risk.largestLoserPnl())),
                new Card("Top concentration", String.format(Locale.US, "%.0f%%", concentration),
                        overConcentrated ? WARN : TEXT, overConcentrated ? "Over-concentrated" : "Diversification OK")));
    }

    private String divergingBars(List<RiskAnalytics.PositionRisk> risks) {
        List<Map.Entry<String, BigDecimal>> entries = pnlBySymbol(risks);
        if (entries.isEmpty()) {
            return muted("No open positions.");
        }
        double max = entries.stream().mapToDouble(entry -> Math.abs(entry.getValue().doubleValue())).max().orElse(0.0);
        StringBuilder table = new StringBuilder("<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" style=\"width:100%;border-collapse:collapse;\">");
        for (Map.Entry<String, BigDecimal> entry : entries) {
            double value = entry.getValue().doubleValue();
            String bar = "<div style=\"height:12px;width:" + barWidth(Math.abs(value), max) + "%;background:"
                    + (value < 0 ? NEGATIVE : POSITIVE) + ";" + (value < 0 ? "margin-left:auto;border-radius:3px 0 0 3px;" : "border-radius:0 3px 3px 0;")
                    + "\"></div>";
            table.append("<tr>")
                    .append("<td style=\"width:64px;padding:3px 8px 3px 0;font-size:12px;font-weight:700;color:").append(TEXT).append(";\">")
                    .append(escape(entry.getKey())).append("</td>")
                    .append("<td align=\"right\" style=\"width:42%;padding:3px 0;\">").append(value < 0 ? bar : "").append("</td>")
                    .append("<td style=\"width:1px;padding:0;background:").append(BORDER).append(";\"></td>")
                    .append("<td style=\"width:42%;padding:3px 0;\">").append(value > 0 ? bar : "").append("</td>")
                    .append("<td style=\"width:88px;padding:3px 0 3px 8px;font-size:12px;font-weight:700;text-align:right;color:")
                    .append(colorOf(entry.getValue())).append(";\">").append(escape(signedMoney(entry.getValue()))).append("</td>")
                    .append("</tr>");
        }
        return table.append("</table>").toString();
    }

    private String exposureBars(List<RiskAnalytics.Exposure> exposures) {
        if (exposures.isEmpty()) {
            return muted("Nothing held.");
        }
        double max = exposures.stream().mapToDouble(exposure -> exposure.value().doubleValue()).max().orElse(0.0);
        StringBuilder table = new StringBuilder("<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" style=\"width:100%;border-collapse:collapse;\">");
        for (RiskAnalytics.Exposure exposure : exposures.subList(0, Math.min(exposures.size(), MAX_EXPOSURE_ROWS))) {
            table.append("<tr>")
                    .append("<td style=\"width:140px;padding:3px 8px 3px 0;font-size:12px;font-weight:700;color:").append(TEXT).append(";\">")
                    .append(escape(exposure.key())).append("</td>")
                    .append("<td style=\"padding:3px 0;\"><div style=\"height:12px;width:")
                    .append(barWidth(exposure.value().doubleValue(), max)).append("%;background:").append(ACCENT)
                    .append(";border-radius:0 3px 3px 0;\"></div></td>")
                    .append("<td style=\"width:150px;padding:3px 0 3px 8px;font-size:12px;text-align:right;color:").append(TEXT).append(";\">")
                    .append(escape(money(exposure.value()))).append(" (")
                    .append(String.format(Locale.US, "%.1f%%", exposure.percentOfTotal())).append(")</td>")
                    .append("</tr>");
        }
        table.append("</table>");
        if (exposures.size() > MAX_EXPOSURE_ROWS) {
            table.append(muted("and " + (exposures.size() - MAX_EXPOSURE_ROWS) + " more"));
        }
        return table.toString();
    }

    private String advisory(List<RiskAnalytics.PositionRisk> risks, RiskAnalytics.RiskVerdict verdict, String emptyText) {
        List<RiskAnalytics.PositionRisk> group = byVerdict(risks, verdict);
        StringBuilder box = new StringBuilder("<div style=\"border:1px solid ").append(BORDER)
                .append(";border-radius:12px;padding:12px 14px;margin:0 0 10px;\">")
                .append("<div style=\"font-size:13px;font-weight:700;color:").append(TEXT).append(";\">")
                .append(escape(advisoryTitle(verdict))).append("</div>");
        if (group.isEmpty()) {
            box.append("<div style=\"margin-top:6px;font-size:12px;color:").append(MUTED).append(";\">").append(escape(emptyText)).append("</div>");
        }
        for (RiskAnalytics.PositionRisk position : group) {
            box.append("<div style=\"margin-top:8px;font-size:12px;\"><b style=\"color:").append(TEXT).append(";\">")
                    .append(escape(position.symbol())).append("</b> <span style=\"color:").append(colorOf(position.unrealizedPnl()))
                    .append(";font-weight:700;\">").append(escape(signedMoney(position.unrealizedPnl())))
                    .append(String.format(Locale.US, " (%+.1f%%)", position.unrealizedPercent())).append("</span>")
                    .append(" <span style=\"color:").append(MUTED).append(";\">· ").append(escape(position.workspaceLabel())).append("</span>")
                    .append("<div style=\"color:#52606d;margin-top:2px;\">").append(escape(position.advice())).append("</div></div>");
        }
        return box.append("</div>").toString();
    }

    // ---- Broker reconciliation -------------------------------------------

    private String brokerSection(PortfolioSnapshot.BrokerCheck check) {
        if (check == null) {
            return "";
        }
        String color = !check.checked() || check.symbolCount() == 0 ? MUTED
                : check.mismatches().isEmpty() ? POSITIVE : NEGATIVE;
        StringBuilder content = new StringBuilder("<div style=\"font-size:13px;font-weight:700;color:").append(color).append(";\">")
                .append(escape(brokerSummary(check))).append("</div>");
        for (PortfolioSnapshot.Mismatch mismatch : check.mismatches()) {
            content.append("<div style=\"margin-top:6px;font-size:12px;\"><b style=\"color:").append(TEXT).append(";\">")
                    .append(escape(mismatch.symbol())).append("</b> <span style=\"color:#52606d;\">")
                    .append(escape(mismatch.description())).append("</span></div>");
        }
        return subsection("Broker reconciliation (NeuralArc vs Alpaca)",
                "Whether NeuralArc's positions agree with what Alpaca holds: shares and average cost for each symbol.",
                "<div style=\"border:1px solid " + BORDER + ";border-radius:12px;padding:12px 14px;\">" + content + "</div>");
    }

    private static String brokerSummary(PortfolioSnapshot.BrokerCheck check) {
        if (!check.checked()) {
            return check.note().isBlank() ? "Alpaca could not be reached, so positions were not compared." : check.note();
        }
        if (check.symbolCount() == 0) {
            return "No tracked positions to compare.";
        }
        if (check.mismatches().isEmpty()) {
            return "All " + (check.symbolCount() == 1 ? "1 symbol matches" : check.symbolCount() + " symbols match") + " Alpaca.";
        }
        int count = check.mismatches().size();
        return count + (count == 1 ? " mismatch" : " mismatches") + " among " + check.symbolCount()
                + (check.symbolCount() == 1 ? " symbol" : " symbols") + ":";
    }

    private static String advisoryTitle(RiskAnalytics.RiskVerdict verdict) {
        return switch (verdict) {
            case PROTECT_GAINS -> "Possible losers — protect gains";
            case WAIT_TO_BOOK_LOSS -> "Possible winners in losing — wait to book losses";
            case CUT_LOSS -> "Cut-loss candidates";
            case ON_TRACK_WINNER -> "On-track winners";
        };
    }

    // ---- Building blocks ---------------------------------------------------

    private String cards(List<Card> cards) {
        int perRow = 4;
        StringBuilder table = new StringBuilder("<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" style=\"width:100%;border-collapse:collapse;\">");
        for (int start = 0; start < cards.size(); start += perRow) {
            table.append("<tr>");
            for (int index = start; index < start + perRow; index++) {
                table.append("<td style=\"width:25%;padding:0 8px 8px 0;vertical-align:top;\">");
                if (index < cards.size()) {
                    Card card = cards.get(index);
                    table.append("<div style=\"border:1px solid ").append(BORDER).append(";border-radius:12px;padding:10px 12px;background:")
                            .append(SOFT).append(";\">")
                            .append("<div style=\"font-size:11px;color:").append(MUTED).append(";text-transform:uppercase;letter-spacing:.6px;\">")
                            .append(escape(card.label())).append("</div>")
                            .append("<div style=\"margin-top:4px;font-size:16px;font-weight:700;color:").append(card.color()).append(";\">")
                            .append(escape(card.value())).append("</div>")
                            .append("<div style=\"margin-top:2px;font-size:11px;color:").append(MUTED).append(";\">")
                            .append(escape(card.note())).append("</div></div>");
                }
                table.append("</td>");
            }
            table.append("</tr>");
        }
        return table.append("</table>").toString();
    }

    private String section(String title, String subtitle, String content) {
        return "<h2 style=\"font-size:16px;margin:22px 0 2px;color:" + TEXT + ";\">" + escape(title) + "</h2>"
                + "<div style=\"font-size:12px;color:" + MUTED + ";margin:0 0 10px;\">" + escape(subtitle) + "</div>" + content;
    }

    private String subsection(String title, String subtitle, String content) {
        return "<h3 style=\"font-size:14px;margin:18px 0 2px;color:" + TEXT + ";\">" + escape(title) + "</h3>"
                + "<div style=\"font-size:12px;color:" + MUTED + ";margin:0 0 8px;\">" + escape(subtitle) + "</div>" + content;
    }

    private String cell(String html, String align, String color, boolean bold) {
        return "<td style=\"text-align:" + align + ";padding:8px 10px;border-bottom:1px solid " + BORDER + ";color:" + color
                + ";" + (bold ? "font-weight:700;" : "") + "\">" + html + "</td>";
    }

    private String muted(String text) {
        return "<div style=\"font-size:12px;color:" + MUTED + ";\">" + escape(text) + "</div>";
    }

    private static String footer(PortfolioSnapshot snapshot) {
        return "Figures are NeuralArc's own strategy accounting for " + snapshot.modeLabel()
                + " mode at the time above, not Alpaca's blended account view. You get this email because Portfolio"
                + " Snapshot Emails are on in Settings, where you can change the times or turn them off. It summarises"
                + " your positions; it is not a recommendation to buy or sell.";
    }

    private static List<Map.Entry<String, BigDecimal>> pnlBySymbol(List<RiskAnalytics.PositionRisk> risks) {
        Map<String, BigDecimal> bySymbol = new LinkedHashMap<>();
        for (RiskAnalytics.PositionRisk position : risks) {
            bySymbol.merge(position.symbol(), position.unrealizedPnl(), BigDecimal::add);
        }
        List<Map.Entry<String, BigDecimal>> entries = new ArrayList<>(bySymbol.entrySet());
        entries.sort(Map.Entry.<String, BigDecimal>comparingByValue().reversed());
        return entries;
    }

    private static List<RiskAnalytics.PositionRisk> byVerdict(List<RiskAnalytics.PositionRisk> risks, RiskAnalytics.RiskVerdict verdict) {
        return risks.stream()
                .filter(position -> position.verdict() == verdict)
                .sorted(Comparator.comparing(RiskAnalytics.PositionRisk::unrealizedPnl))
                .toList();
    }

    private static int barWidth(double value, double max) {
        if (max <= 0 || value <= 0) {
            return 0;
        }
        return (int) Math.max(1, Math.round(value / max * 100));
    }

    private static String winText(PortfolioSnapshot.Figures figures) {
        return figures.sells() == 0 ? "no sells yet"
                : String.format(Locale.US, "%.0f%% of %d %s", figures.winRatePercent(), figures.sells(), figures.sells() == 1 ? "sell" : "sells");
    }

    private static String positions(int count) {
        return count == 1 ? "1 position" : count + " positions";
    }

    static String money(BigDecimal value) {
        BigDecimal rounded = Monetary.round(value == null ? BigDecimal.ZERO : value);
        return (rounded.signum() < 0 ? "-$" : "$") + String.format(Locale.US, "%,.2f", rounded.abs());
    }

    static String signedMoney(BigDecimal value) {
        BigDecimal rounded = Monetary.round(value == null ? BigDecimal.ZERO : value);
        return rounded.signum() > 0 ? "+" + money(rounded) : money(rounded);
    }

    private static String colorOf(BigDecimal value) {
        int sign = value == null ? 0 : Monetary.round(value).signum();
        return sign > 0 ? POSITIVE : sign < 0 ? NEGATIVE : TEXT;
    }

    private static String dash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
