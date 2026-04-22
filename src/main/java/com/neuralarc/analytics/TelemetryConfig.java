package com.neuralarc.analytics;

public record TelemetryConfig(boolean enabled, String endpointUrl, String authorizationHeader, String appVersion) {
}
