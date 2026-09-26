package com.neuralarc.ui;

import com.neuralarc.model.ApplicationMode;
import com.neuralarc.model.BrokerType;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Everything about the saved settings that decides <em>which</em> broker session the app holds.
 *
 * <p>Saving Settings used to drop the trade stream and reconnect whatever had changed — a polling
 * interval, an email time, a theme. Reconnecting costs a stream restart, a fresh broker handshake
 * and a full strategy resync, and during market hours it is a gap in the one thing that must not
 * have gaps. Comparing this before and after a save answers the only question that matters: is the
 * app still talking to the same account, the same way?
 *
 * <p>Secrets are compared, never stored beyond the comparison's lifetime, and never logged.
 */
record ConnectionIdentity(BrokerType brokerType, ApplicationMode mode, Map<ApplicationMode, String> credentials) {
    ConnectionIdentity {
        credentials = credentials == null ? Map.of() : Map.copyOf(credentials);
    }

    /**
     * @param apiKey    the saved key for a mode
     * @param apiSecret the saved secret for a mode
     */
    static ConnectionIdentity of(BrokerType brokerType, ApplicationMode mode,
                                 Function<ApplicationMode, String> apiKey,
                                 Function<ApplicationMode, String> apiSecret) {
        Map<ApplicationMode, String> credentials = new LinkedHashMap<>();
        for (ApplicationMode candidate : ApplicationMode.values()) {
            credentials.put(candidate, value(apiKey, candidate) + "\u0000" + value(apiSecret, candidate));
        }
        return new ConnectionIdentity(brokerType, mode, credentials);
    }

    /** True when a save changed nothing the broker session depends on. */
    boolean sameSessionAs(ConnectionIdentity other) {
        return other != null && equals(other);
    }

    private static String value(Function<ApplicationMode, String> source, ApplicationMode mode) {
        if (source == null) {
            return "";
        }
        String value = source.apply(mode);
        return value == null ? "" : value.trim();
    }
}
