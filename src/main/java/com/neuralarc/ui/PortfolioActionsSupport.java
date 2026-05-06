package com.neuralarc.ui;

import com.neuralarc.model.Position;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

final class PortfolioActionsSupport {
    List<ManagedStrategy> filterTargets(List<ManagedStrategy> strategies, Scope scope) {
        List<ManagedStrategy> targets = new ArrayList<>();
        for (ManagedStrategy entry : strategies) {
            if (scope.matches(entry)) {
                targets.add(entry);
            }
        }
        return targets;
    }

    String buildConfirmationMessage(Scope scope, List<ManagedStrategy> targets) {
        String symbols = targets.stream()
                .limit(6)
                .map(entry -> entry.strategy.symbol())
                .collect(Collectors.joining(", "));
        String ellipsis = targets.size() > 6 ? ", ..." : "";
        return "<html><body style='width:360px'>"
                + "<b>" + scope.confirmHeading(targets.size()) + "</b><br><br>"
                + "Symbols: " + symbols + ellipsis + "<br><br>"
                + "Each strategy will submit a manual limit sell using its latest broker price."
                + "<br>Strategies configured to repeat after exit can re-initiate after the position fully closes."
                + "</body></html>";
    }

    String buildResultMessage(Scope scope, BatchResult result) {
        StringBuilder sb = new StringBuilder("<html><body style='width:360px'>");
        sb.append("<b>").append(scope.menuLabel()).append("</b><br><br>");
        sb.append("Submitted: ").append(result.successes().size());
        if (!result.successes().isEmpty()) {
            sb.append("<br>").append(String.join(", ", result.successes()));
        }
        if (!result.failures().isEmpty()) {
            sb.append("<br><br><b>Failed:</b><br>").append(String.join("<br>", result.failures()));
        }
        sb.append("</body></html>");
        return sb.toString();
    }

    record BatchResult(List<String> successes, List<String> failures) {
    }

    enum Scope {
        PROFITABLE("Sell Profitable Positions") {
            @Override
            boolean matches(ManagedStrategy entry) {
                Position position = entry.cachedPosition();
                return position.getTotalShares() > 0 && position.unrealizedPnl().compareTo(BigDecimal.ZERO) > 0;
            }

            @Override
            String confirmHeading(int count) {
                return "Sell " + count + " profitable position(s)?";
            }

            @Override
            String emptyMessage() {
                return "There are no profitable open positions to sell.";
            }
        },
        ALL_OPEN("Sell All Open Positions") {
            @Override
            boolean matches(ManagedStrategy entry) {
                return entry.cachedPosition().getTotalShares() > 0;
            }

            @Override
            String confirmHeading(int count) {
                return "Sell all " + count + " open position(s)?";
            }

            @Override
            String emptyMessage() {
                return "There are no open positions to sell.";
            }
        },
        LOSS_ONLY("Sell Losing Positions") {
            @Override
            boolean matches(ManagedStrategy entry) {
                Position position = entry.cachedPosition();
                return position.getTotalShares() > 0 && position.unrealizedPnl().compareTo(BigDecimal.ZERO) < 0;
            }

            @Override
            String confirmHeading(int count) {
                return "Sell " + count + " losing position(s)?";
            }

            @Override
            String emptyMessage() {
                return "There are no losing open positions to sell.";
            }
        };

        private final String menuLabel;

        Scope(String menuLabel) {
            this.menuLabel = menuLabel;
        }

        abstract boolean matches(ManagedStrategy entry);

        abstract String confirmHeading(int count);

        abstract String emptyMessage();

        String menuLabel() {
            return menuLabel;
        }

        String dialogTitle() {
            return menuLabel;
        }

        String logPrefix() {
            return "[" + menuLabel + "]";
        }
    }
}
