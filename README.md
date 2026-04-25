# NeuralArc Trader (Java Swing MVP)

## Project overview
NeuralArc Trader is a free and open-source Java Swing desktop trading utility designed as an MVP that is safe-by-default (mock/paper mode), broker-agnostic, and extensible for future broker and analytics backend integrations.

## Features
- Java 22 desktop app using Swing
- Broker abstraction (`TradingApi`) with `MockTradingApi` and `AlpacaTradingApi` stub
- Rule-based strategy engine with 5 rules
- Paper trading ON by default
- Optional encrypted local credential storage (AES-GCM + PBKDF2)
- Opt-in telemetry publishing with queueing, retry, and graceful degradation
- BigDecimal-based money/price/P&L computations

## How rule engine works
For default strategy (NIO):
1. Buy 10 at/under 8.00
2. Activate stop logic at/above 9.00
3. Sell all if price falls below 9.00 after stop loss activation
4. Sell all at/above 10.00
5. Buy +5 at/under 7.00 and +5 at/under 6.00 once each

Duplicate triggers are prevented through strategy state tracking and rule flags.

## How to run
```bash
./gradlew test
./gradlew run
```

## How paper trading works
`MockTradingApi` simulates market data and fills, tracks in-memory positions, and enables safe local strategy testing.

## How credentials are stored
If enabled, credentials are encrypted using AES-GCM with a PBKDF2-derived key and written to:
- `~/.neuralarc/credentials.properties`

Credentials are never emitted to analytics events.

## What telemetry is collected
Selected event types: app lifecycle, strategy start/stop, rule triggered, order events, and position/P&L updates.
Potential fields include userId, broker type, symbol, side, quantity, price, timestamp, session/strategy id, shares, average cost, and realized/unrealized P&L.

## Telemetry consent and privacy note
Telemetry is disabled by default and enabled only by explicit user consent.
See `PRIVACY.md`.

## How to configure analytics endpoint
Use Settings dialog to set endpoint (example: `http://localhost:8080/events`) and enable telemetry.

## How to add a new broker
1. Implement `TradingApi`
2. Add a broker enum value in `BrokerType`
3. Register implementation in `TradingApiFactory`
4. Add any UI options/credentials you need

## Future roadmap
- Real Alpaca integration (auth, orders, market data)
- Historical charts and richer analytics dashboard
- Plugin-style rule definitions
- Multi-symbol and portfolio strategies

## License suggestion
Recommended: MIT or Apache-2.0 for friendly open-source collaboration.
