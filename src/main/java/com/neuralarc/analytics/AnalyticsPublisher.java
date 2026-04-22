package com.neuralarc.analytics;

public interface AnalyticsPublisher {
    void publish(AnalyticsEvent event);
    void shutdown();
}
