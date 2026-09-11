package com.neuralarc.ui;

import com.neuralarc.api.AlpacaMarketDataApi;
import com.neuralarc.model.MarketBar;

import javax.swing.SwingWorker;
import java.awt.Frame;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Opens a strategy's stock chart. The window appears at once in a loading state; the daily prices are
 * fetched and the indicators computed off the UI thread, then the chart fills in. Only live market data
 * is used: without Alpaca credentials the window says so instead of drawing anything.
 */
final class StockChartOpener {
    /** Three years on screen, plus about 300 earlier trading days so the 200-day average is ready from the first bar shown. */
    static final int HISTORY_CALENDAR_DAYS = 3 * 366 + 430;
    private static final Logger LOGGER = Logger.getLogger(StockChartOpener.class.getName());

    private StockChartOpener() {
    }

    static void open(Frame owner, ManagedStrategy entry, String context, Supplier<AlpacaMarketDataApi> marketData) {
        if (entry == null || entry.strategy == null) {
            return;
        }
        String symbol = entry.strategy.symbol();
        // Read from the row's cached snapshot on the EDT, before handing off to the background load.
        List<StockChartLevels.Level> levels = StockChartLevels.from(entry);
        StockChartDialog dialog = new StockChartDialog(owner, symbol, context);
        dialog.setVisible(true);

        AlpacaMarketDataApi api = marketData == null ? null : marketData.get();
        if (api == null) {
            dialog.view().showMessage("Connect to Alpaca to see this chart",
                    "The chart is drawn from live daily prices. Connect your Alpaca account in Settings, "
                            + "then open the chart again.");
            return;
        }
        new SwingWorker<StockChartData, Void>() {
            @Override
            protected StockChartData doInBackground() throws Exception {
                LocalDate end = LocalDate.now();
                List<MarketBar> bars = api.getDailyBars(symbol, end.minusDays(HISTORY_CALENDAR_DAYS), end);
                return StockChartData.from(symbol, bars, levels);
            }

            @Override
            protected void done() {
                if (!dialog.isDisplayable()) {
                    return; // Closed while loading.
                }
                try {
                    StockChartData data = get();
                    if (data.isEmpty()) {
                        dialog.view().showMessage("No price history for " + symbol,
                                "Alpaca returned no daily prices for this symbol. It may be newly listed, "
                                        + "delisted, or not traded on US exchanges.");
                    } else {
                        dialog.view().showData(data);
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException ex) {
                    Throwable cause = ex.getCause() == null ? ex : ex.getCause();
                    LOGGER.log(Level.WARNING, "Stock chart load failed for " + symbol, cause);
                    dialog.view().showMessage("Couldn't load the chart for " + symbol,
                            "Alpaca did not return the price history (" + cause.getMessage() + "). "
                                    + "Try again in a moment.");
                }
            }
        }.execute();
    }
}
