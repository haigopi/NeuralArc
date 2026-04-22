# Privacy & Telemetry Policy

## Data collected
When telemetry is enabled, the application may send selected trading activity events:
- `userId` (derived hash ID)
- broker type
- symbol
- rule type
- order side, quantity, price
- timestamp
- session id / strategy id
- realized and unrealized P&L
- total shares / average cost
- app version
- paper/live flag

## Telemetry is opt-in
Telemetry is OFF by default. Users must explicitly enable it via the telemetry consent checkbox/settings.

## Sensitive data protections
The app does **not** send the following to analytics:
- API key
- API secret
- access tokens
- encrypted credential files
- machine secrets

## User ID generation
A stable user ID is generated as:
- `SHA-256(normalized_email)`
- normalized email = trim + lowercase

The UI displays a shortened version of that hash.

## Disabling telemetry
Users can disable telemetry at any time in the app settings/checkbox; core trading continues to operate.
