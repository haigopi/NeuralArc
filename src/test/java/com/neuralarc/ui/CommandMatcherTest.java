package com.neuralarc.ui;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandMatcherTest {
    private final List<CommandMatcher.Command> commands = List.of(
            command("Cancel Unfilled Entry Limit Buys", "Cancel Buys", "withdraws the staged entry buy"),
            command("Cancel Loss-Level Limit Buys", "Cancel Buys", "withdraws averaging-down buys"),
            command("Cancel All Stop Losses", "Stop Losses", "switches stop-loss monitoring off"),
            command("Resume All", "Lifecycle", "resumes paused strategies and their stop losses"),
            command("Sell Losing Positions", "Sell Positions", "limit-sells every losing position"));

    @Test
    void typingPartOfTheNameFindsTheAction() {
        List<CommandMatcher.Command> found = CommandMatcher.match(commands, "stop loss");

        assertEquals("Cancel All Stop Losses", found.get(0).label());
    }

    @Test
    void theLeadingSlashIsHowYouSayCommandAndIsNotSearchedFor() {
        assertEquals("cancel staged", CommandMatcher.clean("  //Cancel   Staged "));
        assertEquals(CommandMatcher.match(commands, "resume"),
                CommandMatcher.match(commands, "/resume"));
    }

    @Test
    void everyTypedWordHasToAppearSomewhere() {
        assertTrue(CommandMatcher.match(commands, "cancel banana").isEmpty(),
                "a query may only narrow the list; it must never wander to an unrelated action");
        assertEquals(2, CommandMatcher.match(commands, "cancel limit").size());
    }

    @Test
    void aNameMatchOutranksADescriptionMatch() {
        List<CommandMatcher.Command> found = CommandMatcher.match(commands, "stop");

        assertEquals("Cancel All Stop Losses", found.get(0).label(), "the action named for it comes first");
        assertEquals("Resume All", found.get(1).label(), "and one that only mentions it in passing still matches");
    }

    @Test
    void anExactNameWinsOutright() {
        assertEquals("Resume All", CommandMatcher.match(commands, "Resume All").get(0).label());
    }

    @Test
    void aBlankQueryListsEverythingInMenuOrder() {
        assertEquals(commands, CommandMatcher.match(commands, "   "));
        assertEquals(commands, CommandMatcher.match(commands, "/"));
    }

    @Test
    void tiesKeepTheOrderTheMenuUses() {
        List<CommandMatcher.Command> found = CommandMatcher.match(commands, "cancel");

        assertEquals(List.of("Cancel Unfilled Entry Limit Buys", "Cancel Loss-Level Limit Buys",
                "Cancel All Stop Losses"), found.stream().map(CommandMatcher.Command::label).toList());
    }

    private static CommandMatcher.Command command(String label, String group, String description) {
        return new CommandMatcher.Command(label, group, description, true, () -> { });
    }
}
