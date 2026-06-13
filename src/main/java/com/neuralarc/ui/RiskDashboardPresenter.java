package com.neuralarc.ui;

import com.neuralarc.analytics.RiskAnalytics;
import com.neuralarc.service.ReconciliationService;

import java.math.BigDecimal;

/**
 * Builds the HTML body shown in the {@link RiskDashboardDialog} from the pure
 * {@link RiskAnalytics.Report} and {@link ReconciliationService.Report}. Separated from the Swing
 * dialog so the content is unit-testable.
 */
final class RiskDashboardPresenter {
    String buildHtml(String modeLabel, RiskAnalytics.Report risk, ReconciliationService.Report reconciliation) {
        StringBuilder html = new StringBuilder("<html><body style='width:520px'>");
        html.append("<b>Strategy Risk Dashboard — ").append(escape(modeLabel)).append("</b><br><br>");

        html.append("<b>Capital allocated:</b> ").append(money(risk.totalCapital())).append("<br>");
        html.append("<b>Top-symbol concentration:</b> ").append(percent(risk.topSymbolConcentrationPercent()))
                .append(concentrationWarning(risk.topSymbolConcentrationPercent())).append("<br>");
        html.append("<b>Largest winner:</b> ").append(escape(blankToDash(risk.largestWinnerSymbol())))
                .append(" (").append(money(risk.largestWinnerPnl())).append(")<br>");
        html.append("<b>Largest loser:</b> ").append(escape(blankToDash(risk.largestLoserSymbol())))
                .append(" (").append(money(risk.largestLoserPnl())).append(")<br><br>");

        html.append("<b>Exposure by symbol</b><br>");
        appendExposures(html, risk.exposureBySymbol());
        html.append("<br><b>Exposure by strategy</b><br>");
        appendExposures(html, risk.exposureByWorkspace());

        html.append("<br><b>Strategy P&amp;L ranking</b><br>");
        if (risk.workspacePnlRanking().isEmpty()) {
            html.append("&nbsp;&nbsp;(none)<br>");
        } else {
            for (RiskAnalytics.Exposure row : risk.workspacePnlRanking()) {
                html.append("&nbsp;&nbsp;").append(escape(row.key())).append(": ").append(money(row.value())).append("<br>");
            }
        }

        html.append("<br><b>Broker reconciliation</b> (NeuralArc vs Alpaca)<br>");
        if (!reconciliation.hasMismatches()) {
            html.append("&nbsp;&nbsp;All ").append(reconciliation.lines().size())
                    .append(" symbol(s) reconcile.<br>");
        } else {
            html.append("&nbsp;&nbsp;<span style='color:#B71C1C;'>")
                    .append(reconciliation.mismatchCount()).append(" mismatch(es):</span><br>");
            for (ReconciliationService.Line line : reconciliation.lines()) {
                if (line.status() == ReconciliationService.Status.MATCH) {
                    continue;
                }
                html.append("&nbsp;&nbsp;&nbsp;&nbsp;").append(escape(line.symbol())).append(": ")
                        .append(describe(line)).append("<br>");
            }
            html.append("<br><i>NeuralArc never auto-corrects — review the broker account manually.</i><br>");
        }
        return html.append("</body></html>").toString();
    }

    private void appendExposures(StringBuilder html, java.util.List<RiskAnalytics.Exposure> exposures) {
        if (exposures.isEmpty()) {
            html.append("&nbsp;&nbsp;(none)<br>");
            return;
        }
        for (RiskAnalytics.Exposure exposure : exposures) {
            html.append("&nbsp;&nbsp;").append(escape(exposure.key())).append(": ")
                    .append(money(exposure.value())).append(" (").append(percent(exposure.percentOfTotal())).append(")<br>");
        }
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

    private String concentrationWarning(double percent) {
        return percent >= 60.0 ? " <span style='color:#B71C1C;'>(over-concentrated)</span>" : "";
    }

    private String money(BigDecimal value) {
        if (value == null) {
            return "$0.00";
        }
        return value.signum() < 0 ? "-$" + value.abs().toPlainString() : "$" + value.toPlainString();
    }

    private String plain(BigDecimal value) {
        return value == null ? "0" : value.stripTrailingZeros().toPlainString();
    }

    private String percent(double value) {
        return String.format("%.1f%%", value);
    }

    private String blankToDash(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }

    private String escape(String value) {
        return value == null ? "" : value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
