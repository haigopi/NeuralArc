package com.neuralarc.ui;

import com.neuralarc.model.ApplicationMode;
import com.neuralarc.model.BrokerType;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConnectionIdentityTest {
    private final Map<ApplicationMode, String> keys = new HashMap<>(Map.of(
            ApplicationMode.PAPER, "paper-key", ApplicationMode.LIVE, "live-key"));
    private final Map<ApplicationMode, String> secrets = new HashMap<>(Map.of(
            ApplicationMode.PAPER, "paper-secret", ApplicationMode.LIVE, "live-secret"));

    @Test
    void savingSettingsThatTouchNoCredentialKeepsTheSameSession() {
        ConnectionIdentity before = identity(BrokerType.ALPACA, ApplicationMode.LIVE);

        assertTrue(identity(BrokerType.ALPACA, ApplicationMode.LIVE).sameSessionAs(before),
                "an email time or a polling interval must not cost a reconnect");
    }

    @Test
    void aChangedKeyOrSecretNeedsANewSession() {
        ConnectionIdentity before = identity(BrokerType.ALPACA, ApplicationMode.LIVE);

        keys.put(ApplicationMode.LIVE, "rotated-key");
        assertFalse(identity(BrokerType.ALPACA, ApplicationMode.LIVE).sameSessionAs(before));

        keys.put(ApplicationMode.LIVE, "live-key");
        secrets.put(ApplicationMode.LIVE, "rotated-secret");
        assertFalse(identity(BrokerType.ALPACA, ApplicationMode.LIVE).sameSessionAs(before),
                "a secret rotated behind the same key is still a different session");
    }

    @Test
    void theOtherModesCredentialsCountToo() {
        ConnectionIdentity before = identity(BrokerType.ALPACA, ApplicationMode.LIVE);

        keys.put(ApplicationMode.PAPER, "new-paper-key");

        assertFalse(identity(BrokerType.ALPACA, ApplicationMode.LIVE).sameSessionAs(before),
                "the paper client is wired at the same time; a stale one would keep trading the old account");
    }

    @Test
    void switchingModeOrBrokerNeedsANewSession() {
        ConnectionIdentity live = identity(BrokerType.ALPACA, ApplicationMode.LIVE);

        assertFalse(identity(BrokerType.ALPACA, ApplicationMode.PAPER).sameSessionAs(live));
    }

    @Test
    void whitespaceAroundAPastedKeyIsNotAChange() {
        ConnectionIdentity before = identity(BrokerType.ALPACA, ApplicationMode.LIVE);

        keys.put(ApplicationMode.LIVE, "  live-key  ");

        assertTrue(identity(BrokerType.ALPACA, ApplicationMode.LIVE).sameSessionAs(before));
    }

    private ConnectionIdentity identity(BrokerType broker, ApplicationMode mode) {
        return ConnectionIdentity.of(broker, mode, keys::get, secrets::get);
    }
}
