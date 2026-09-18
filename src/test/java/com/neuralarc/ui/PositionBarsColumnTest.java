package com.neuralarc.ui;

import com.neuralarc.model.MarketBar;
import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.awt.image.BufferedImage;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static com.neuralarc.ui.AverageDownTestEntries.position;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PositionBarsColumnTest {
    private final List<String> fetched = new ArrayList<>();
    private final List<ManagedStrategy> opened = new ArrayList<>();
    private boolean connected = true;
    private final MutableClock clock = new MutableClock(Instant.parse("2026-09-18T15:00:00Z"));

    @Test
    void showsTheBarsAndHowTheMarketIsTreatingTheStock() throws Exception {
        PositionBarsColumn column = column();

        onEdt(() -> column.show(position("NIO", 10, "4.71", "3.68"), false));
        flushEdt();

        assertEquals(List.of("NIO"), fetched);
        assertFalse(column.chart().data().isEmpty());
        assertTrue(column.verdictText().startsWith("How the market is treating it: "), column.verdictText());
        assertFalse(column.summaryText().isBlank());
    }

    @Test
    void frequentPanelRefreshesDoNotRefetchTheSameSymbol() throws Exception {
        PositionBarsColumn column = column();
        ManagedStrategy nio = position("NIO", 10, "4.71", "3.68");

        onEdt(() -> column.show(nio, false));
        flushEdt();
        onEdt(() -> column.show(nio, false));
        onEdt(() -> column.show(nio, false));
        flushEdt();

        assertEquals(List.of("NIO"), fetched);
    }

    @Test
    void cachedBarsAreRefetchedOnceTheyExpire() throws Exception {
        PositionBarsColumn column = column();
        ManagedStrategy nio = position("NIO", 10, "4.71", "3.68");
        onEdt(() -> column.show(nio, false));
        flushEdt();

        clock.advance(PositionBarsColumn.CACHE_TTL.plusSeconds(1));
        onEdt(() -> column.show(nio, false));
        flushEdt();

        assertEquals(List.of("NIO", "NIO"), fetched);
    }

    @Test
    void aSlowLoadForAStockNoLongerSelectedDoesNotOverwriteTheNewOne() throws Exception {
        List<Runnable> pending = new ArrayList<>();
        PositionBarsColumn column = new PositionBarsColumn(host(), pending::add, clock);
        ManagedStrategy nio = position("NIO", 10, "4.71", "3.68");
        ManagedStrategy aapl = position("AAPL", 5, "200", "190");

        onEdt(() -> column.show(nio, false));
        onEdt(() -> column.show(aapl, false));
        pending.get(1).run(); // AAPL arrives first
        flushEdt();
        pending.get(0).run(); // then the stale NIO load
        flushEdt();

        assertSame(aapl, column.entry());
        assertEquals("AAPL", column.chart().data().symbol());
    }

    @Test
    void withoutAConnectionItSaysHowToGetTheBars() throws Exception {
        connected = false;
        PositionBarsColumn column = column();

        onEdt(() -> column.show(position("NIO", 10, "4.71", "3.68"), false));

        assertTrue(fetched.isEmpty());
        assertTrue(column.summaryText().contains("Connect your Alpaca account"), column.summaryText());
    }

    @Test
    void clickingTheColumnOpensTheFullChartForItsPosition() throws Exception {
        PositionBarsColumn column = column();
        ManagedStrategy nio = position("NIO", 10, "4.71", "3.68");
        onEdt(() -> column.show(nio, true));
        flushEdt();

        onEdt(() -> column.dispatchEvent(new java.awt.event.MouseEvent(column, java.awt.event.MouseEvent.MOUSE_CLICKED,
                0, 0, 10, 10, 1, false, java.awt.event.MouseEvent.BUTTON1)));

        assertEquals(List.of(nio), opened);
    }

    @Test
    void theFallbackIsTheFirstLosingPosition() {
        assertTrue(PositionBarsSelection.isLosing(position("NIO", 10, "4.71", "3.68")));
        assertFalse(PositionBarsSelection.isLosing(position("AAPL", 5, "190", "200")), "a winner");
        assertFalse(PositionBarsSelection.isLosing(position("MSFT", 0, "400", "380")), "no shares held");
    }

    @Test
    void theCompactChartPaintsAndFitsTheBarsToItsWidth() throws Exception {
        PositionBarsColumn column = column();
        onEdt(() -> column.show(position("NIO", 10, "4.71", "3.68"), false));
        flushEdt();
        PositionBarsChart chart = column.chart();
        chart.setSize(420, 150);
        chart.paint(new BufferedImage(420, 150, BufferedImage.TYPE_INT_ARGB).createGraphics());

        assertEquals(52, PositionBarsChart.visibleBars(366, 300));
        assertEquals(PositionBarsChart.MAX_BARS, PositionBarsChart.visibleBars(5000, 300));
        assertEquals(10, PositionBarsChart.visibleBars(5000, 10));
    }

    private PositionBarsColumn column() {
        return new PositionBarsColumn(host(), Runnable::run, clock);
    }

    private PositionBarsColumn.Host host() {
        return new PositionBarsColumn.Host() {
            @Override
            public PositionBarsColumn.BarsSource barsSource() {
                return connected ? (symbol, start, end) -> {
                    fetched.add(symbol);
                    List<MarketBar> bars = new ArrayList<>();
                    for (MarketBar bar : StockChartTestBars.fromCloses(StockChartTestBars.steady(8, 4, 300))) {
                        bars.add(new MarketBar(symbol, bar.timestamp(), bar.open(), bar.high(), bar.low(), bar.close(), bar.volume()));
                    }
                    return bars;
                } : null;
            }

            @Override
            public void openFullChart(ManagedStrategy entry) {
                opened.add(entry);
            }
        };
    }

    private static void onEdt(Runnable body) throws Exception {
        SwingUtilities.invokeAndWait(body);
    }

    private static void flushEdt() throws Exception {
        SwingUtilities.invokeAndWait(() -> { });
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
