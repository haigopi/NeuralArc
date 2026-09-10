package com.neuralarc.ui;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StockImportTextParserTest {
    @Test
    void readsANumberedNameToTickerWatchlist() {
        // Copied out of a chat client: word joiners sit between the list number and the name.
        String paste = " 1.⁠ ⁠Palantir ~ $PLTR\n"
                + " 2.⁠ ⁠Nebius ~ $NBIS\n"
                + " 3.⁠ ⁠Oracle ~ $ORCL\n"
                + " 4.⁠ ⁠ServiceNow ~ $NOW\n"
                + " 5.⁠ ⁠Rocket Lab ~ $RKLB\n"
                + " 6.⁠ ⁠SpaceX ~ $SPCX\n"
                + " 7.⁠ ⁠SpaceMobile ~ $ASTS\n"
                + " 8.⁠ ⁠Redwire ~ $RDW\n"
                + " 9.⁠ ⁠CoreWeave ~ $CRWV\n"
                + "10.⁠ ⁠Zeta Global ~ $ZETA\n";

        List<PortfolioStockImportDialog.ImportedStockDraft> drafts = StockImportTextParser.parse(paste);

        assertEquals(
                List.of("PLTR", "NBIS", "ORCL", "NOW", "RKLB", "SPCX", "ASTS", "RDW", "CRWV", "ZETA"),
                drafts.stream().map(PortfolioStockImportDialog.ImportedStockDraft::symbol).toList());
        assertTrue(drafts.stream().allMatch(PortfolioStockImportDialog.ImportedStockDraft::autoPriced),
                "a ticker-only paste carries no prices, so levels must be calculated");
        assertTrue(drafts.stream().allMatch(draft -> draft.targets().isEmpty()));
    }

    @Test
    void acceptsTickerListVariants() {
        List<PortfolioStockImportDialog.ImportedStockDraft> drafts = StockImportTextParser.parse("""
                Watchlist for Monday

                - $PLTR
                • $NBIS
                3) Oracle - $ORCL
                $BRK.B
                """);

        assertEquals(List.of("PLTR", "NBIS", "ORCL", "BRK.B"),
                drafts.stream().map(PortfolioStockImportDialog.ImportedStockDraft::symbol).toList());
    }

    @Test
    void collapsesRepeatedTickersToTheirFirstAppearance() {
        List<PortfolioStockImportDialog.ImportedStockDraft> drafts = StockImportTextParser.parse("""
                1. Palantir ~ $PLTR
                2. Nebius ~ $NBIS
                3. Palantir again ~ $PLTR
                """);

        assertEquals(List.of("PLTR", "NBIS"),
                drafts.stream().map(PortfolioStockImportDialog.ImportedStockDraft::symbol).toList());
    }

    @Test
    void ignoresProseThatHappensToHaveNoTicker() {
        List<PortfolioStockImportDialog.ImportedStockDraft> drafts = StockImportTextParser.parse("""
                Top picks for the quarter
                AI Utilities
                1. Nebius ~ $NBIS
                """);

        assertEquals(List.of("NBIS"),
                drafts.stream().map(PortfolioStockImportDialog.ImportedStockDraft::symbol).toList());
    }

    @Test
    void stillReadsAlertBlocksWithTheirOwnPrices() {
        List<PortfolioStockImportDialog.ImportedStockDraft> drafts = StockImportTextParser.parse("""
                @everyone type: DT/Swing
                Symbol: $MDB
                Entry: Entered @ 451
                Stop: Below 446 (Aggressive) - 15% from the entry.
                Targets: 466

                @everyone type: DT/Swing
                Symbol: $TEAM
                Entry: Entered @ 191.60
                Stop: Below 183 (Very strict stop) (Aggressive) - 15% from the entry.
                Targets: 204 - 218
                """);

        assertEquals(2, drafts.size());
        assertEquals("MDB", drafts.getFirst().symbol());
        assertEquals(new BigDecimal("451"), drafts.getFirst().recommendedEntry());
        assertEquals(new BigDecimal("446"), drafts.getFirst().stopLoss());
        assertFalse(drafts.getFirst().autoPriced(), "pasted prices must be used as given");
        assertEquals(List.of(new BigDecimal("204"), new BigDecimal("218")), drafts.get(1).targets());
    }

    @Test
    void stillReadsAnalystTargetLists() {
        List<PortfolioStockImportDialog.ImportedStockDraft> drafts = StockImportTextParser.parse("""
                WALL STREET’S MOST BULLISH PRICE TARGETS FOR POPULAR STOCKS

                AI Utilities
                • $IREN $105 (+151%)
                • $NBIS $410 (+87%)
                """);

        assertEquals(2, drafts.size());
        assertEquals("IREN", drafts.getFirst().symbol());
        assertEquals(new BigDecimal("41.83"), drafts.getFirst().recommendedEntry());
        assertEquals(new BigDecimal("35.56"), drafts.getFirst().stopLoss());
        assertFalse(drafts.getFirst().autoPriced());
    }

    @Test
    void aSymbolLineInsideAnAlertIsNotMistakenForATickerList() {
        // "Symbol: $MDB" is a ticker with no price on the line, so the block parser must win.
        List<PortfolioStockImportDialog.ImportedStockDraft> drafts = StockImportTextParser.parse("""
                Symbol: $MDB
                Entry: Entered @ 451
                Stop: Below 446
                Targets: 466
                """);

        assertEquals(1, drafts.size());
        assertFalse(drafts.getFirst().autoPriced());
        assertEquals(new BigDecimal("451"), drafts.getFirst().recommendedEntry());
    }

    @Test
    void returnsNothingForBlankInput() {
        assertTrue(StockImportTextParser.parse(null).isEmpty());
        assertTrue(StockImportTextParser.parse("   \n  ").isEmpty());
    }

    @Test
    void theTipsPanelSampleParsesAsATickerList() {
        List<PortfolioStockImportDialog.ImportedStockDraft> drafts =
                StockImportTextParser.parse(StockImportTipsPanel.TICKER_LIST_SAMPLE);

        assertEquals(List.of("PLTR", "NBIS", "ORCL", "RKLB"),
                drafts.stream().map(PortfolioStockImportDialog.ImportedStockDraft::symbol).toList());
    }
}
