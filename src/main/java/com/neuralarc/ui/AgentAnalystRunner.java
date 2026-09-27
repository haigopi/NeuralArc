package com.neuralarc.ui;

import com.neuralarc.agent.AgentLoop;
import com.neuralarc.agent.AgentRunAudit;
import com.neuralarc.agent.AnthropicAgentModel;
import com.neuralarc.agent.ReadOnlyToolset;
import com.neuralarc.agent.ToolRegistry;
import com.neuralarc.agent.ToolSession;
import com.neuralarc.agent.tools.PositionSnapshots;
import com.neuralarc.api.AlpacaMarketDataApi;
import com.neuralarc.api.TradingApi;
import com.neuralarc.db.SqliteAgentToolCallRepository;
import com.neuralarc.model.AgentSettings;
import com.neuralarc.service.AlpacaNewsClient;
import com.neuralarc.service.MarketHoursService;

import java.time.Clock;
import java.util.UUID;

/**
 * Runs one analyst question: assembles the read-only toolset, the model and the audit, then drives
 * the loop. Everything here happens on a background thread — the loop blocks on HTTP.
 *
 * <p>The toolset is rebuilt per run from the live services, so a run started while disconnected
 * simply offers fewer tools rather than failing halfway through, and a key changed in Settings takes
 * effect on the next run without a restart.
 */
final class AgentAnalystRunner {
    private static final java.util.logging.Logger LOGGER =
            java.util.logging.Logger.getLogger(AgentAnalystRunner.class.getName());

    /** What the frame lends the agent. Any of these may be null when the app is not connected. */
    interface Services {
        TradingApi tradingApi();

        AlpacaMarketDataApi marketDataApi();

        AlpacaNewsClient newsClient();

        MarketHoursService marketHours();

        PositionSnapshots positions();

        com.neuralarc.agent.tools.PortfolioActionCatalog actionCatalog();

        SqliteAgentToolCallRepository auditRepository();

        AgentSettings settings();
    }

    record Outcome(String runId, String text, int toolCalls, int turns, boolean completed, boolean refused) {
    }

    static final String SYSTEM_PROMPT = """
            You are the analyst inside NeuralArc, a desktop trading console the operator runs for their own \
            account. Answer from the tools, never from memory: you do not know today's date, the market's \
            state, or what the operator holds until you call for them. Call market_session before anything \
            time-sensitive, and open_positions before commenting on the book.

            You can only read. You cannot place, change or cancel an order, and nothing you write is executed: \
            the operator reads it and decides. Never say or imply that you have bought, sold or positioned \
            anything.

            Prefer auto_analyze for levels, because those are the numbers the operator sees on screen; reach \
            for daily_bars only when you need the shape of the move. Quote prices as the tools give them.

            You can also read the operator's Portfolio Actions menu with portfolio_actions, and see how many of             their positions each action would touch. Use it to end on something they can actually do — name the             action as the menu names it, say how many rows it would act on, and leave the running of it to them.             You cannot run one.

            Write for a busy operator: a short plain-language read, the two or three things that stand out, \
            and for each one the level or event that would change it. Say plainly when the data does not \
            support an answer. Do not pad, do not repeat the tool output back, and do not give blanket \
            financial advice — stick to what these positions and these prices show.
            """;

    private final Services services;

    AgentAnalystRunner(Services services) {
        this.services = services;
    }

    /** True when a run could start right now: enabled, keyed, and with at least one tool to call. */
    boolean ready() {
        AgentSettings settings = services.settings();
        return settings != null && settings.ready();
    }

    /** One question, one answer, nothing kept: what the Ask the Analyst dialog does. */
    Outcome run(String question) throws Exception {
        return newConversation().ask(question);
    }

    /**
     * A conversation the operator can keep talking to.
     *
     * <p>The model, the toolset and the budget are built once and shared by every message, so a
     * follow-up like "and the second one?" lands on a model that still has the first answer — and so
     * the tool-call budget covers the whole conversation rather than resetting with each question,
     * which is what stops a long chat quietly costing several runs' worth of calls.
     */
    Conversation newConversation() {
        return new Conversation();
    }

    final class Conversation {
        private String runId;
        private ToolSession session;
        private AgentLoop loop;
        private int messages;

        /** Asks the next question in this conversation. Blocks; call it off the EDT. */
        Outcome ask(String question) throws Exception {
            if (loop == null) {
                start();
            }
            messages++;
            int message = messages;
            LOGGER.info(() -> "[AGENT][RUN][MESSAGE " + message + "] run=" + runId
                    + " budgetLeft=" + session.callsRemaining());
            AgentLoop.Result result = loop.run(question);
            return new Outcome(runId, result.text(), result.toolCalls(), result.turns(),
                    result.completed(), result.refused());
        }

        /** Tool calls left before the budget stops this conversation. */
        int callsRemaining() {
            return session == null ? Integer.MAX_VALUE : session.callsRemaining();
        }

        int messageCount() {
            return messages;
        }

        String runId() {
            return runId;
        }

        /** Everything is read at the first question, so a key or connection fixed since counts. */
        private void start() {
            AgentSettings settings = services.settings();
            if (settings == null || !settings.ready()) {
                throw new IllegalStateException("Add an Anthropic API key in Settings and enable the AI analyst first.");
            }
            if (services.marketDataApi() == null) {
                // Positions alone would let a run start and be billed while the analyst could only read
                // stale snapshots — no prices, no levels, no news. Stop before the first token.
                throw new IllegalStateException("The analyst has no market data to read. Connect to Alpaca in Settings first.");
            }
            ToolRegistry registry = ReadOnlyToolset.create(
                    services.tradingApi(),
                    services.marketDataApi(),
                    services.newsClient(),
                    services.marketHours(),
                    services.positions(),
                    services.actionCatalog(),
                    Clock.systemDefaultZone());
            if (registry.all().isEmpty()) {
                throw new IllegalStateException("The analyst has nothing to read. Connect to Alpaca in Settings first.");
            }
            runId = UUID.randomUUID().toString();
            LOGGER.info(() -> "[AGENT][RUN][NEW] run=" + runId + " model=" + settings.model()
                    + " maxTurns=" + settings.maxTurns() + " maxToolCalls=" + settings.maxToolCalls()
                    + " tools=" + registry.all().size());
            session = new ToolSession(registry, settings.maxToolCalls(),
                    new AgentRunAudit(services.auditRepository(), runId, Clock.systemUTC()));
            loop = new AgentLoop(
                    AnthropicAgentModel.withApiKey(settings.apiKey(), registry, SYSTEM_PROMPT, settings.model()),
                    session,
                    settings.maxTurns());
        }
    }
}
