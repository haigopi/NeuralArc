package com.neuralarc.ui;

import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BulkProgressDialogTest {
    @Test
    void theWindowKeepsUpWithTheRun() throws Exception {
        BulkProgressDialog dialog = dialog("Re-enter Inactive Stocks", 3);

        onEdt(() -> {
            dialog.advance("AAPL: placed at $12.40", BulkProgress.Outcome.DONE);
            dialog.advance("MSFT: skipped, still held at the broker", BulkProgress.Outcome.SKIPPED);
        });

        assertEquals("2 of 3", dialog.progressText());
        assertEquals("2 of 3 · MSFT", dialog.headlineText(), "the heading names the stock, not the whole line");
        assertEquals(2, dialog.listedLines().size());
    }

    @Test
    void aCleanRunClosesItselfRatherThanWaitingForAClick() throws Exception {
        BulkProgressDialog dialog = dialog("Cancel All Pending Limit Buys", 2);

        onEdt(() -> {
            dialog.advance("AAPL", BulkProgress.Outcome.DONE);
            dialog.advance("MSFT", BulkProgress.Outcome.DONE);
            dialog.finish(List.of("AAPL", "MSFT"), List.of(), List.of());
        });

        assertTrue(dialog.finished());
        assertFalse(dialog.isDisplayable(), "nothing failed, so there is nothing to read");
    }

    @Test
    void aFailureKeepsTheWindowOpenAndShowsOnlyWhatFailed() throws Exception {
        BulkProgressDialog dialog = dialog("Sell All Positions", 3);

        onEdt(() -> {
            dialog.advance("AAPL", BulkProgress.Outcome.DONE);
            dialog.advance("MSFT", BulkProgress.Outcome.DONE);
            dialog.advance("TSLA: failed, rejected", BulkProgress.Outcome.FAILED);
            dialog.finish(List.of("AAPL", "MSFT"), List.of(), List.of("TSLA: failed, rejected"));
        });

        assertTrue(dialog.isDisplayable(), "an order that did not go in must not be closed over");
        assertEquals("2 done · 1 failed", dialog.headlineText());
        assertEquals(List.of("TSLA: failed, rejected"),
                dialog.listedLines().stream().map(BulkProgress.Line::text).toList());
        assertEquals("Close", dialog.closeButtonText());
        assertTrue(dialog.getTitle().endsWith("2 done · 1 failed"));
        onEdt(dialog::dispose);
    }

    @Test
    void aRunThatDiedOutrightSaysSoInsteadOfVanishing() throws Exception {
        BulkProgressDialog dialog = dialog("Reposition Expired", 4);

        onEdt(() -> dialog.abort("Failed to complete portfolio action: connection reset"));

        assertTrue(dialog.isDisplayable());
        assertEquals(1, dialog.listedLines().size());
        onEdt(dialog::dispose);
    }

    @Test
    void oneItemNeedsNoWindow() {
        assertEquals(BulkProgressHandle.NONE, BulkProgressDialog.open(null, "Sell Position", 1));
        assertEquals(BulkProgressHandle.NONE, BulkProgressDialog.open(null, "Nothing", 0));
    }

    private static BulkProgressDialog dialog(String title, int total) throws Exception {
        BulkProgressDialog[] holder = new BulkProgressDialog[1];
        SwingUtilities.invokeAndWait(() -> holder[0] = new BulkProgressDialog(null, title, total));
        return holder[0];
    }

    private static void onEdt(Runnable action) throws Exception {
        SwingUtilities.invokeAndWait(action);
    }
}
