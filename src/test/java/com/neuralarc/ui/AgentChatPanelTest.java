package com.neuralarc.ui;

import com.neuralarc.agent.tools.PortfolioActionCatalog;
import com.neuralarc.agent.tools.PositionSnapshots;
import com.neuralarc.api.AlpacaMarketDataApi;
import com.neuralarc.api.TradingApi;
import com.neuralarc.db.SqliteAgentToolCallRepository;
import com.neuralarc.model.AgentSettings;
import com.neuralarc.service.AlpacaNewsClient;
import com.neuralarc.service.MarketHoursService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentChatPanelTest {
    @Test
    void theTabOpensSayingWhatTheAnalystCanAndCannotDo() {
        AgentChatPanel panel = new AgentChatPanel(() -> null);

        assertTrue(panel.transcriptText().contains("cannot place, change or cancel any order"));
        assertEquals(AgentChatPanel.PLACEHOLDER,
                panel.inputField().getClientProperty("JTextField.placeholderText"));
    }

    @Test
    void withTheAnalystOffTheQuestionIsKeptAndTheOperatorIsToldWhere() {
        AgentChatPanel panel = new AgentChatPanel(() -> null);
        panel.inputField().setText("what should I watch?");

        panel.send();

        assertTrue(panel.statusText().contains("Settings"));
        assertEquals("what should I watch?", panel.inputField().getText(),
                "a question must not vanish because the analyst was not configured");
    }

    @Test
    void aSlashLineRunsACommandInsteadOfSpendingTokensOnIt() {
        java.util.List<String> opened = new java.util.ArrayList<>();
        AgentChatPanel panel = new AgentChatPanel(() -> null, opened::add);
        panel.inputField().setText("/cancel staged");

        panel.send();

        assertEquals(java.util.List.of("/cancel staged"), opened, "the palette takes it, not the model");
        assertEquals("", panel.inputField().getText());
        assertTrue(panel.statusText().contains("cancel staged"));
        assertFalse(panel.transcriptText().contains("You"), "a command is not part of the conversation");
    }

    @Test
    void theOpeningNoteExplainsTheSlash() {
        assertTrue(new AgentChatPanel(() -> null).transcriptText().contains("/cancel staged"));
    }

    @Test
    void anEmptyMessageDoesNothingAtAll() {
        AgentChatPanel panel = new AgentChatPanel(() -> null);
        panel.inputField().setText("   ");

        panel.send();

        assertEquals(" ", panel.statusText());
        assertTrue(panel.askButton().isEnabled());
    }

    @Test
    void startingOverSaysTheAnalystHasForgotten() {
        AgentChatPanel panel = new AgentChatPanel(() -> null);

        panel.startOver();

        assertTrue(panel.transcriptText().contains("forgotten what was said"));
        assertFalse(panel.transcriptText().contains("cannot place, change or cancel"),
                "the opening note belongs to the conversation that was cleared");
    }

    @Test
    void aConversationKeepsOneBudgetAcrossItsMessages() {
        AgentAnalystRunner runner = new AgentAnalystRunner(services(
                new AgentSettings(true, "sk-ant-test", "claude-opus-5", 4, 10)));

        AgentAnalystRunner.Conversation conversation = runner.newConversation();

        assertEquals(0, conversation.messageCount());
        // Not started, so nothing is spent and nothing reached the model.
        assertEquals(Integer.MAX_VALUE, conversation.callsRemaining());
        assertTrue(conversation.runId() == null);
    }

    @Test
    void aConversationWithNoMarketDataStopsBeforeSpendingAnything() {
        AgentAnalystRunner.Conversation conversation = new AgentAnalystRunner(services(
                new AgentSettings(true, "sk-ant-test", "claude-opus-5", 4, 10))).newConversation();

        IllegalStateException thrown = org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateException.class, () -> conversation.ask("what now?"));

        assertTrue(thrown.getMessage().contains("Connect to Alpaca"));
    }

    /** Disconnected services: no broker, no market data, no news. */
    private static AgentAnalystRunner.Services services(AgentSettings settings) {
        return new AgentAnalystRunner.Services() {
            @Override public TradingApi tradingApi() { return null; }
            @Override public AlpacaMarketDataApi marketDataApi() { return null; }
            @Override public AlpacaNewsClient newsClient() { return null; }
            @Override public MarketHoursService marketHours() { return null; }
            @Override public PositionSnapshots positions() { return List::of; }
            @Override public PortfolioActionCatalog actionCatalog() { return null; }
            @Override public SqliteAgentToolCallRepository auditRepository() { return null; }
            @Override public AgentSettings settings() { return settings; }
        };
    }
}
