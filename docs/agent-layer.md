# The read-only analyst agent (`com.neuralarc.agent`)

Status: **implemented, not yet exercised against the live API.**
Scope: the AI analyst wired into Portfolio Actions, its tools, its audit trail and its settings.

The agent is a *consumer* of NeuralArc, not a new path into it. Every tool wraps a service the
app already had, so an agent read and an operator read return the same numbers, and the model
never holds a broker client. It can only read: `ToolRegistry.readOnly()` refuses, at construction,
any tool whose `Effect` is above `READ_ONLY`, so granting write access is a deliberate edit to
`ReadOnlyToolset` rather than something a prompt can talk its way into.

Follows the rules in `AGENTS.md`: nothing here runs on the EDT, the API key is stored encrypted,
and the audit table arrived through an append-only migration (`025_agent_tool_calls`).

---

## 1. Architecture

Everything below the EDT boundary runs on a `SwingWorker`; everything below the read-only
barrier can only read.

```mermaid
flowchart TB
    subgraph edt["Swing EDT — paint, click, apply state"]
        settings["AgentSettingsPanel<br/>key · model · two limits"]
        dialog["AgentAnalystDialog<br/>question, answer, what it cost"]
    end

    subgraph bg["Background executor — model calls, broker I/O"]
        runner["AgentAnalystRunner<br/>builds this run's toolset"]
        loop["AgentLoop<br/>max exchanges"]
        model["AnthropicAgentModel<br/>Anthropic Java SDK"]
        session["ToolSession<br/>dispatch · budget · audit"]
    end

    subgraph tools["Read-only tools — ToolRegistry.readOnly()"]
        t1["market_session"]
        t2["latest_price"]
        t3["daily_bars"]
        t4["auto_analyze"]
        t5["latest_news"]
        t6["open_positions"]
    end

    services["Existing NeuralArc services (unchanged)<br/>TradingApi · AlpacaMarketDataApi · AutoAnalyzeService<br/>AlpacaNewsClient · MarketHoursService · PositionSnapshots"]

    keys[("app_settings<br/>key encrypted")]
    audit[("agent_tool_calls<br/>migration 025")]
    claude{{"Claude API"}}
    alpaca{{"Alpaca"}}

    settings -->|"writes the key, encrypted"| keys
    keys -->|"read each run"| runner
    dialog -->|"run(question) on a SwingWorker"| runner
    runner -->|drives| loop
    loop <-->|"start / respond · turn"| model
    model <-->|HTTPS| claude
    loop -->|"call(name, args)"| session
    session -->|"1 row per call"| audit
    session -->|"only READ_ONLY tools exist here"| tools
    tools -->|"plain Java calls"| services
    services -->|REST| alpaca
```

`open_positions` deliberately reads the UI's cached book through `PositionSnapshots`, never the
broker: asking a question must not set off a broker sweep.

---

## 2. One run, end to end

Three guards stop a run early, and each of them costs nothing.

```mermaid
sequenceDiagram
    autonumber
    actor Operator
    participant Dialog as AgentAnalystDialog (EDT)
    participant Run as Runner + AgentLoop (SwingWorker)
    participant Claude
    participant Tools as ToolSession + tools
    participant DB as SQLite

    Operator->>Dialog: Portfolio Actions › Ask the Analyst
    Note over Dialog: stop 1 — no key, or analyst off:<br/>nothing is billed
    Dialog->>Run: run(question) — off the EDT
    Note over Run: stop 2 — not connected to Alpaca:<br/>no prices to read
    Run->>Claude: question + system prompt + 6 tool schemas

    loop until Claude answers, or a limit stops it
        Claude-->>Run: tool_use: auto_analyze(NVDA), latest_price(NVDA)
        Run->>Tools: call(name, args) — arguments validated here
        Tools->>DB: one row: tool, ok, arguments, ms
        Tools-->>Run: JSON result, or a readable error the model can correct
        Run->>Claude: every result of this turn, in one message
        Note over Run: stop 3 — budget spent: the model is told<br/>to answer with what it has
    end

    Claude-->>Run: final text
    Run-->>Dialog: Outcome(text, toolCalls, turns, completed)
    Dialog-->>Operator: the answer, and what the run cost
```

Two details the drawing is there to make explicit:

- **All of a turn's tool results go back in one message.** Splitting them across messages trains
  the model out of asking for tools in parallel.
- **A failed call still spends budget.** That is what makes a retry loop terminate.

Limits are counted in `AgentLoop` (exchanges) and `ToolSession` (tool calls), never in the
prompt — a model can talk itself out of an instruction, not out of a counter.

---

## 3. Class interaction

```mermaid
classDiagram
    class AgentTool {
        <<interface>>
        +name() String
        +description() String
        +parameters() ToolParameters
        +effect() Effect
        +call(ToolArguments) JSONObject
    }
    class Effect {
        <<enumeration>>
        READ_ONLY
        PROPOSAL
        TRADING
    }
    class AgentModel {
        <<interface>>
        +start(String) AgentTurn
        +respond(List~ToolOutcome~) AgentTurn
    }
    class Audit {
        <<interface>>
        +toolCalled(ToolResult, JSONObject, Duration)
    }
    class ToolRegistry {
        +readOnly(tools) ToolRegistry$
        +proposing(tools) ToolRegistry$
        +definitions() JSONArray
    }
    class ToolSession {
        -int maxCalls
        +call(String, JSONObject) ToolResult
    }
    class AgentLoop {
        -int maxTurns
        +run(String) Result
    }
    class AnthropicAgentModel {
        -List~MessageParam~ conversation
    }
    class AgentRunAudit {
        -String runId
    }

    AgentAnalystRunner --> ReadOnlyToolset : asks for a registry
    AgentAnalystRunner --> AgentLoop : drives
    ReadOnlyToolset --> ToolRegistry : builds
    ToolRegistry o-- AgentTool : registers READ_ONLY only
    AgentTool --> Effect : declares
    AgentTool --> ToolParameters : declares
    AgentTool --> ToolArguments : validates with
    AgentLoop --> AgentModel : asks for turns
    AgentLoop --> ToolSession : runs each call
    AgentModel --> AgentTurn : returns
    AgentTurn *-- ToolCall
    AnthropicAgentModel ..|> AgentModel
    ToolSession o-- ToolRegistry : dispatches through
    ToolSession --> ToolResult : returns
    ToolSession --> Audit : reports every call
    AgentRunAudit ..|> Audit
    AgentRunAudit --> SqliteAgentToolCallRepository : writes through
    SqliteAgentToolCallRepository --> AgentToolCall : rows, capped per run

    MarketSessionTool ..|> AgentTool
    LatestPriceTool ..|> AgentTool
    DailyBarsTool ..|> AgentTool
    AutoAnalyzeTool ..|> AgentTool
    LatestNewsTool ..|> AgentTool
    OpenPositionsTool ..|> AgentTool
```

Two interfaces carry the design:

- **`AgentModel`** keeps the loop testable with no key and no network, and lets a second provider
  drop in without touching the loop.
- **`ToolSession.Audit`** keeps persistence out of the loop: nothing in `agent/` except
  `AgentRunAudit` knows SQLite exists.

**The edge that is not in the diagram is the point.** No class here reaches `StrategyService`,
`TradingApi.placeBuyOrder`, or any repository that writes a strategy.

---

## 4. Files

| Area | Classes |
| --- | --- |
| Agent core | `agent/AgentLoop`, `AgentModel`, `AgentTurn`, `ToolSession`, `ToolRegistry`, `ToolResult`, `AgentTool`, `ToolParameters`, `ToolArguments`, `ReadOnlyToolset` |
| Provider | `agent/AnthropicAgentModel` (Anthropic Java SDK, `claude-opus-5` by default) |
| Tools | `agent/tools/` — `MarketSessionTool`, `LatestPriceTool`, `DailyBarsTool`, `AutoAnalyzeTool`, `LatestNewsTool`, `OpenPositionsTool`, `PositionSnapshots` |
| Audit | `agent/AgentRunAudit`, `db/SqliteAgentToolCallRepository`, `model/AgentToolCall`, `AppDatabase` migration `025_agent_tool_calls` |
| UI | `ui/AgentAnalystDialog`, `ui/AgentAnalystRunner`, `ui/AgentSettingsPanel`, entry point in `PortfolioActionsMenuBuilder` |
| Settings | `model/AgentSettings`, `AppSettingsService.loadAgentSettings()` / `saveAgentSettings()` (key stored encrypted) |

## 5. Extending it

Adding a tool that only reads: implement `AgentTool` with `Effect.READ_ONLY`, register it in
`ReadOnlyToolset.create(...)`, and write the description as prompt text — it is what the model
reads to decide whether to call it.

Adding a tool that *writes* is a deliberate step down a different path: give it
`Effect.PROPOSAL`, build the registry with `ToolRegistry.proposing(...)`, and have it write a
strategy with status `CREATED` into its own workspace for a human to review and activate. Nothing
should ever carry `Effect.TRADING` without the operator asking for it explicitly.
