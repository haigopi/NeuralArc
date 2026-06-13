package com.neuralarc.ui;

import com.neuralarc.analytics.WorkspaceAccounting;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkspaceSummaryPresenterTest {
    private final WorkspaceSummaryPresenter presenter = new WorkspaceSummaryPresenter();

    @Test
    void formatsKeyFiguresWithSignedMoney() {
        WorkspaceAccounting.Snapshot snapshot = new WorkspaceAccounting.Snapshot(
                new BigDecimal("1245.00"),
                new BigDecimal("-72.24"),
                new BigDecimal("1172.76"),
                new BigDecimal("40.00"),
                new BigDecimal("3500.00"),
                3,
                12,
                75.0
        );
        String line = presenter.summaryLine("Momentum Lab", snapshot);

        assertTrue(line.startsWith("Momentum Lab"), line);
        assertTrue(line.contains("Realized $1245.00"), line);
        assertTrue(line.contains("Unrealized -$72.24"), line);
        assertTrue(line.contains("Total $1172.76"), line);
        assertTrue(line.contains("Win 75%"), line);
        assertTrue(line.contains("Open 3"), line);
        assertTrue(line.contains("Closed 12"), line);
        assertTrue(line.contains("Capital $3500.00"), line);
    }
}
