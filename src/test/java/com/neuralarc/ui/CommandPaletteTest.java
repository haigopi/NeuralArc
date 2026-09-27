package com.neuralarc.ui;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandPaletteTest {
    private final AtomicInteger ran = new AtomicInteger();
    private final List<CommandMatcher.Command> commands = List.of(
            new CommandMatcher.Command("Cancel All Stop Losses", "Stop Losses", "switches monitoring off", true, ran::incrementAndGet),
            new CommandMatcher.Command("Resume All", "Lifecycle", "resumes paused strategies", true, ran::incrementAndGet),
            new CommandMatcher.Command("Promote All to Live", "Lifecycle",
                    "Promote All to Live is unavailable while viewing LIVE mode.", false, ran::incrementAndGet));

    @Test
    void itOpensPreFilledFromWhatWasTypedInTheChat() {
        CommandPalette palette = new CommandPalette(null, commands, null, "/stop loss");

        assertEquals("stop loss", palette.queryField().getText());
        assertEquals("Cancel All Stop Losses", palette.selected().label());
        palette.dispose();
    }

    @Test
    void theFirstMatchIsSelectedSoEnterAlwaysRunsSomethingSensible() {
        CommandPalette palette = new CommandPalette(null, commands, null, "");

        palette.queryField().setText("resume");

        assertEquals("Resume All", palette.selected().label());
        assertEquals(1, palette.shown().size());
        palette.dispose();
    }

    @Test
    void aDisabledActionIsRefusedWithTheMenusOwnReason() {
        CommandPalette palette = new CommandPalette(null, commands, null, "promote");

        palette.runSelected();

        assertEquals(0, ran.get(), "an unavailable action must not run");
        assertTrue(palette.hintText().contains("unavailable while viewing LIVE mode"));
        assertTrue(palette.isDisplayable(), "and the palette stays open to say so");
        palette.dispose();
    }

    @Test
    void noMatchSaysSoRatherThanShowingAnEmptyBox() {
        CommandPalette palette = new CommandPalette(null, commands, null, "banana");

        assertTrue(palette.shown().isEmpty());
        assertEquals("No action matches that.", palette.hintText());
        palette.dispose();
    }

    @Test
    void runningACommandClosesThePaletteFirstSoTheConfirmationOwnsTheScreen() throws Exception {
        CommandPalette palette = new CommandPalette(null, commands, null, "resume");

        palette.runSelected();

        assertTrue(!palette.isDisplayable());
        javax.swing.SwingUtilities.invokeAndWait(() -> { });
        assertEquals(1, ran.get(), "the action itself runs after the palette is gone");
    }
}
