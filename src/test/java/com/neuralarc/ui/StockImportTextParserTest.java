package com.neuralarc.ui;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
    void recoversTheListWhenTheCopyDroppedItsLineBreaks() {
        // Copying out of some chat clients and PDFs delivers the whole list as one run of text.
        String oneLine = "1.\u2060 \u2060Palantir ~ $PLTR 2.\u2060 \u2060Nebius ~ $NBIS"
                + " 3.\u2060 \u2060Oracle ~ $ORCL 4.\u2060 \u2060ServiceNow ~ $NOW"
                + " 10.\u2060 \u2060Zeta Global ~ $ZETA";

        List<PortfolioStockImportDialog.ImportedStockDraft> drafts = StockImportTextParser.parse(oneLine);

        assertEquals(List.of("PLTR", "NBIS", "ORCL", "NOW", "ZETA"),
                drafts.stream().map(PortfolioStockImportDialog.ImportedStockDraft::symbol).toList());
    }

    @Test
    void acceptsEveryLineBreakConvention() {
        List<String> items = List.of("1. Palantir ~ $PLTR", "2. Nebius ~ $NBIS", "3. Oracle ~ $ORCL");
        for (String separator : List.of("\n", "\r\n", "\r", "\u2028", "\u2029")) {
            List<PortfolioStockImportDialog.ImportedStockDraft> drafts =
                    StockImportTextParser.parse(String.join(separator, items));

            assertEquals(List.of("PLTR", "NBIS", "ORCL"),
                    drafts.stream().map(PortfolioStockImportDialog.ImportedStockDraft::symbol).toList(),
                    "separator " + separator.codePoints().boxed().toList());
        }
    }

    @Test
    void alertBlocksUseCarriageReturnsToo() {
        List<PortfolioStockImportDialog.ImportedStockDraft> drafts = StockImportTextParser.parse(
                "Symbol: $MDB\r\nEntry: Entered @ 451\r\nStop: Below 446\r\nTargets: 466");

        assertEquals(1, drafts.size());
        assertEquals("MDB", drafts.getFirst().symbol());
        assertEquals(new BigDecimal("451"), drafts.getFirst().recommendedEntry());
    }

    @Test
    void oneUnreadableAlertBlockNoLongerDiscardsTheRest() {
        List<PortfolioStockImportDialog.ImportedStockDraft> drafts = StockImportTextParser.parse("""
                Symbol: $MDB
                Entry: Entered @ 451
                Stop: Below 446
                Targets: 466

                Symbol: $BROKEN
                Entry: Entered @ 100

                Symbol: $TEAM
                Entry: Entered @ 191.60
                Stop: Below 183
                Targets: 204
                """);

        assertEquals(List.of("MDB", "TEAM"),
                drafts.stream().map(PortfolioStockImportDialog.ImportedStockDraft::symbol).toList());
    }

    @Test
    void reportsTheProblemWhenNoBlockCanBeRead() {
        assertThrows(IllegalArgumentException.class, () -> StockImportTextParser.parse("""
                Symbol: $BROKEN
                Entry: Entered @ 100
                """));
    }

    @Test
    void readsTheListWhateverStandsInForTheLineBreak() {
        // A copy can deliver this list with the line break replaced by a zero-width character, a
        // space, a tab, or nothing at all. Every entry must survive regardless.
        String wordJoiner = "\u2060";
        List<String> items = List.of(
                "1." + wordJoiner + " " + wordJoiner + "Palantir ~ $PLTR",
                "2." + wordJoiner + " " + wordJoiner + "Nebius ~ $NBIS",
                "3." + wordJoiner + " " + wordJoiner + "Oracle ~ $ORCL",
                "10." + wordJoiner + " " + wordJoiner + "Zeta Global ~ $ZETA");
        List<String> separators = List.of(
                "\n", "\r", "\r\n", "\u2028", "\u2029", "\u0085",
                " ", "\u00A0", "\t", "\u000B", "\f", wordJoiner, "\u200B", "");

        for (String separator : separators) {
            List<PortfolioStockImportDialog.ImportedStockDraft> drafts =
                    StockImportTextParser.parse(String.join(separator, items));

            assertEquals(List.of("PLTR", "NBIS", "ORCL", "ZETA"),
                    drafts.stream().map(PortfolioStockImportDialog.ImportedStockDraft::symbol).toList(),
                    "separator codepoints " + separator.codePoints().boxed().toList());
        }
    }

    @Test
    void aTickerRunningStraightIntoTheNextEntryIsStillRead() {
        // The pathological case: the separator vanished entirely, so "$PLTR" abuts "2.".
        List<PortfolioStockImportDialog.ImportedStockDraft> drafts =
                StockImportTextParser.parse("1. Palantir ~ $PLTR2. Nebius ~ $NBIS3. Oracle ~ $ORCL");

        assertEquals(List.of("PLTR", "NBIS", "ORCL"),
                drafts.stream().map(PortfolioStockImportDialog.ImportedStockDraft::symbol).toList());
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
