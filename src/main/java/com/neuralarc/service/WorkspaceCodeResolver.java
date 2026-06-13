package com.neuralarc.service;

import com.neuralarc.util.ClientOrderId;

/**
 * Resolves a strategy's workspace code for embedding in its Alpaca {@code client_order_id}.
 *
 * <p>Returns {@link ClientOrderId#UNASSIGNED_CODE} ("ALL") for strategies with no workspace, so the
 * order id is always well-formed. Injected (via setters) into the order path so the engine can tag
 * every order with its owning strategy workspace for reconciliation and auditing.
 */
@FunctionalInterface
public interface WorkspaceCodeResolver {
    String codeForWorkspace(String workspaceId);

    /** Default resolver used until a real one is wired in; always reports the unassigned code. */
    static WorkspaceCodeResolver unassigned() {
        return workspaceId -> ClientOrderId.UNASSIGNED_CODE;
    }
}
