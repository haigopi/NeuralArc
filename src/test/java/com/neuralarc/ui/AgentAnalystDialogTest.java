package com.neuralarc.ui;

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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentAnalystDialogTest {
    @Test
    void anEmptyQuestionIsRefusedBeforeAnythingIsSpent() {
        AgentAnalystDialog dialog = new AgentAnalystDialog(null, new AgentAnalystRunner(services(AgentSettings.defaults())));

        dialog.questionField().setText("   ");
        dialog.start();

        assertEquals("Type a question first.", dialog.statusText());
        assertTrue(dialog.askButton().isEnabled(), "nothing ran, so the button stays live");
        assertEquals(AgentAnalystDialog.HINT, dialog.answerText(), "an empty question spends nothing");
        dialog.dispose();
    }

    @Test
    void theDialogOpensWithAQuestionAlreadyWorthAsking() {
        AgentAnalystDialog dialog = new AgentAnalystDialog(null, new AgentAnalystRunner(services(AgentSettings.defaults())));

        assertEquals(AgentAnalystDialog.DEFAULT_QUESTION, dialog.questionField().getText());
        dialog.dispose();
    }

    @Test
    void aRunWithoutAKeySaysWhatToDoInsteadOfCallingTheApi() {
        AgentAnalystRunner runner = new AgentAnalystRunner(services(AgentSettings.defaults()));

        assertTrue(!runner.ready());
        IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> runner.run("What now?"));
        assertTrue(thrown.getMessage().contains("Settings"));
    }

    @Test
    void aConfiguredAnalystWithNoMarketDataStopsBeforeSpendingAnything() {
        AgentAnalystRunner runner = new AgentAnalystRunner(
                services(new AgentSettings(true, "sk-ant-test", "claude-opus-5", 4, 10)));

        assertTrue(runner.ready(), "the settings are complete, so the dialog would offer to run");
        IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> runner.run("What now?"));
        assertTrue(thrown.getMessage().contains("Connect to Alpaca"),
                "cached positions without prices are not worth billing a run for, so it never reaches the model");
    }

    /** Disconnected services: no broker, no market data, no news — what the app looks like at rest. */
    private static AgentAnalystRunner.Services services(AgentSettings settings) {
        return new AgentAnalystRunner.Services() {
            @Override public TradingApi tradingApi() { return null; }
            @Override public AlpacaMarketDataApi marketDataApi() { return null; }
            @Override public AlpacaNewsClient newsClient() { return null; }
            @Override public MarketHoursService marketHours() { return null; }
            @Override public PositionSnapshots positions() { return List::of; }
            @Override public SqliteAgentToolCallRepository auditRepository() { return null; }
            @Override public AgentSettings settings() { return settings; }
        };
    }
}
