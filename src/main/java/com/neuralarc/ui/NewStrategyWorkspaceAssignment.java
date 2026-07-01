package com.neuralarc.ui;

import com.neuralarc.model.Strategy;
import com.neuralarc.service.WorkspaceService;

final class NewStrategyWorkspaceAssignment {
    private NewStrategyWorkspaceAssignment() {
    }

    static void apply(Strategy strategy, String selectedWorkspaceId, WorkspaceService workspaceService) {
        if (strategy == null || workspaceService == null
                || selectedWorkspaceId == null || selectedWorkspaceId.isBlank()) {
            return;
        }
        workspaceService.findById(selectedWorkspaceId)
                .filter(workspace -> workspace.mode() == strategy.mode())
                .ifPresent(workspace -> strategy.setWorkspaceId(workspace.id()));
    }
}
