package com.neuralarc.model;

public record AiProviderHealthStatus(
        AiProviderType providerType,
        boolean healthy,
        String statusText,
        String detail
) {
    public AiProviderHealthStatus {
        statusText = statusText == null ? "" : statusText.trim();
        detail = detail == null ? "" : detail.trim();
    }
}
