package com.neuralarc.ui;

import com.neuralarc.service.AppSettingsService;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public final class MarketStatusPresenter {
    private static final ZoneId US_EASTERN = ZoneId.of("America/New_York");
    private static final DateTimeFormatter ET_TOOLTIP_FORMAT = DateTimeFormatter.ofPattern("EEE h:mm a");

    public MarketStatusViewModel present(
            AppSettingsService.AppSettings settings,
            boolean regularMarketOpen,
            boolean tradingSessionOpen,
            Instant now,
            Instant nextOpen
    ) {
        boolean extendedEnabled = settings != null && settings.extendedHoursTradingEnabled();
        String label = regularMarketOpen
                ? "Market: Open (Regular)"
                : (tradingSessionOpen && extendedEnabled ? "Market: Open (Extended)" : "Market: Closed");
        boolean openForUi = regularMarketOpen || (tradingSessionOpen && extendedEnabled);

        ZonedDateTime nowEastern = (now == null ? Instant.now() : now).atZone(US_EASTERN);
        ZonedDateTime nextOpenEastern = (nextOpen == null ? Instant.now() : nextOpen).atZone(US_EASTERN);
        String state = regularMarketOpen
                ? "Regular session is currently open."
                : (tradingSessionOpen && extendedEnabled)
                ? "Extended session is currently open."
                : "Market session is currently closed.";
        String tooltip = state
                + " Regular: 9:30 AM-4:00 PM ET."
                + " Extended (if enabled): 4:00 AM-8:00 PM ET."
                + " Now (ET): " + nowEastern.format(ET_TOOLTIP_FORMAT) + "."
                + " Next open (ET): " + nextOpenEastern.format(ET_TOOLTIP_FORMAT) + ".";

        return new MarketStatusViewModel(label, tooltip, openForUi);
    }

    public record MarketStatusViewModel(String label, String tooltip, boolean openForUi) {
    }
}

