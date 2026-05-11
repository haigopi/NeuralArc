package com.neuralarc.service;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Month;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.HashSet;
import java.util.Set;

public class MarketHoursService {
    static final ZoneId US_EASTERN = ZoneId.of("America/New_York");
    static final LocalTime REGULAR_OPEN = LocalTime.of(9, 30);
    static final LocalTime REGULAR_CLOSE = LocalTime.of(16, 0);
    static final LocalTime EXTENDED_OPEN = LocalTime.of(4, 0);
    static final LocalTime EXTENDED_CLOSE = LocalTime.of(20, 0);

    private final Clock clock;

    public MarketHoursService() {
        this(Clock.system(US_EASTERN));
    }

    MarketHoursService(Clock clock) {
        this.clock = clock;
    }

    public boolean isRegularMarketHours() {
        return isRegularMarketHours(Instant.now(clock));
    }

    public boolean isRegularMarketHours(Instant instant) {
        ZonedDateTime eastern = instant.atZone(US_EASTERN);
        if (isClosedDay(eastern.toLocalDate())) {
            return false;
        }
        LocalTime time = eastern.toLocalTime();
        return !time.isBefore(REGULAR_OPEN) && time.isBefore(REGULAR_CLOSE);
    }

    public boolean isTradingSessionOpen(boolean extendedHoursEnabled) {
        return isTradingSessionOpen(Instant.now(clock), extendedHoursEnabled);
    }

    public boolean isTradingSessionOpen(Instant instant, boolean extendedHoursEnabled) {
        return isTradingSessionOpen(instant, extendedHoursEnabled, false);
    }

    public boolean isTradingSessionOpen(Instant instant, boolean extendedHoursEnabled, boolean overnightHoursEnabled) {
        ZonedDateTime eastern = instant.atZone(US_EASTERN);
        if (extendedHoursEnabled && overnightHoursEnabled) {
            return isExtendedOrOvernightSessionOpen(eastern);
        }
        if (extendedHoursEnabled) {
            return isExtendedSessionOpen(eastern);
        }
        if (isClosedDay(eastern.toLocalDate())) {
            return false;
        }
        LocalTime time = eastern.toLocalTime();
        return !time.isBefore(REGULAR_OPEN) && time.isBefore(REGULAR_CLOSE);
    }

    public Instant nextMarketOpen(boolean extendedHoursEnabled) {
        return nextMarketOpen(Instant.now(clock), extendedHoursEnabled);
    }

    public Instant nextMarketOpen(Instant instant, boolean extendedHoursEnabled) {
        return nextMarketOpen(instant, extendedHoursEnabled, false);
    }

    public Instant nextMarketOpen(Instant instant, boolean extendedHoursEnabled, boolean overnightHoursEnabled) {
        if (isTradingSessionOpen(instant, extendedHoursEnabled, overnightHoursEnabled)) {
            return instant;
        }
        if (extendedHoursEnabled && overnightHoursEnabled) {
            return nextExtendedSessionOpen(instant);
        }
        if (extendedHoursEnabled) {
            return nextStandardExtendedOpen(instant);
        }

        ZonedDateTime eastern = instant.atZone(US_EASTERN);
        LocalDate date = eastern.toLocalDate();
        LocalTime openTime = REGULAR_OPEN;

        if (!isClosedDay(date) && eastern.toLocalTime().isBefore(openTime)) {
            return ZonedDateTime.of(date, openTime, US_EASTERN).toInstant();
        }

        LocalDate candidate = date.plusDays(1);
        while (isClosedDay(candidate)) {
            candidate = candidate.plusDays(1);
        }
        return ZonedDateTime.of(candidate, openTime, US_EASTERN).toInstant();
    }

    public boolean isClosedDay(LocalDate date) {
        return isWeekend(date) || marketHolidays(date.getYear()).contains(date);
    }

    private boolean isWeekend(LocalDate date) {
        DayOfWeek day = date.getDayOfWeek();
        return day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;
    }

    private boolean isExtendedSessionOpen(ZonedDateTime eastern) {
        LocalDate date = eastern.toLocalDate();
        if (isClosedDay(date)) {
            return false;
        }
        LocalTime time = eastern.toLocalTime();
        return !time.isBefore(EXTENDED_OPEN) && time.isBefore(EXTENDED_CLOSE);
    }

    private boolean isExtendedOrOvernightSessionOpen(ZonedDateTime eastern) {
        LocalDate date = eastern.toLocalDate();
        LocalTime time = eastern.toLocalTime();
        if (!time.isBefore(EXTENDED_OPEN) && time.isBefore(EXTENDED_CLOSE)) {
            return !isClosedDay(date);
        }
        if (!time.isBefore(EXTENDED_CLOSE)) {
            LocalDate nextDate = date.plusDays(1);
            return !isClosedDay(nextDate);
        }
        return !isClosedDay(date);
    }

    private Instant nextStandardExtendedOpen(Instant instant) {
        ZonedDateTime eastern = instant.atZone(US_EASTERN);
        LocalDate date = eastern.toLocalDate();

        if (!isClosedDay(date) && eastern.toLocalTime().isBefore(EXTENDED_OPEN)) {
            return ZonedDateTime.of(date, EXTENDED_OPEN, US_EASTERN).toInstant();
        }

        LocalDate candidate = date.plusDays(1);
        while (isClosedDay(candidate)) {
            candidate = candidate.plusDays(1);
        }
        return ZonedDateTime.of(candidate, EXTENDED_OPEN, US_EASTERN).toInstant();
    }

    private Instant nextExtendedSessionOpen(Instant instant) {
        ZonedDateTime eastern = instant.atZone(US_EASTERN);
        Instant best = null;
        for (int i = 0; i < 14; i++) {
            LocalDate date = eastern.toLocalDate().plusDays(i);
            if (!isClosedDay(date)) {
                Instant dayStart = ZonedDateTime.of(date, EXTENDED_OPEN, US_EASTERN).toInstant();
                if (dayStart.isAfter(instant)) {
                    best = minInstant(best, dayStart);
                }
            }
            LocalDate nextDate = date.plusDays(1);
            if (!isClosedDay(nextDate)) {
                Instant overnightStart = ZonedDateTime.of(date, EXTENDED_CLOSE, US_EASTERN).toInstant();
                if (overnightStart.isAfter(instant)) {
                    best = minInstant(best, overnightStart);
                }
            }
        }
        if (best != null) {
            return best;
        }
        LocalDate fallback = eastern.toLocalDate().plusDays(1);
        while (isClosedDay(fallback)) {
            fallback = fallback.plusDays(1);
        }
        return ZonedDateTime.of(fallback, EXTENDED_OPEN, US_EASTERN).toInstant();
    }

    private Instant minInstant(Instant current, Instant candidate) {
        if (current == null) {
            return candidate;
        }
        return candidate.isBefore(current) ? candidate : current;
    }

    private Set<LocalDate> marketHolidays(int year) {
        Set<LocalDate> holidays = new HashSet<>();
        holidays.add(observed(LocalDate.of(year, Month.JANUARY, 1)));
        holidays.add(nthWeekday(year, Month.JANUARY, DayOfWeek.MONDAY, 3)); // MLK
        holidays.add(nthWeekday(year, Month.FEBRUARY, DayOfWeek.MONDAY, 3)); // Presidents
        holidays.add(goodFriday(year));
        holidays.add(lastWeekday(year, Month.MAY, DayOfWeek.MONDAY)); // Memorial
        holidays.add(observed(LocalDate.of(year, Month.JUNE, 19))); // Juneteenth
        holidays.add(observed(LocalDate.of(year, Month.JULY, 4)));
        holidays.add(nthWeekday(year, Month.SEPTEMBER, DayOfWeek.MONDAY, 1)); // Labor
        holidays.add(nthWeekday(year, Month.NOVEMBER, DayOfWeek.THURSDAY, 4)); // Thanksgiving
        holidays.add(observed(LocalDate.of(year, Month.DECEMBER, 25)));
        return holidays;
    }

    private LocalDate observed(LocalDate date) {
        return switch (date.getDayOfWeek()) {
            case SATURDAY -> date.minusDays(1);
            case SUNDAY -> date.plusDays(1);
            default -> date;
        };
    }

    private LocalDate nthWeekday(int year, Month month, DayOfWeek dayOfWeek, int ordinal) {
        return LocalDate.of(year, month, 1)
                .with(TemporalAdjusters.dayOfWeekInMonth(ordinal, dayOfWeek));
    }

    private LocalDate lastWeekday(int year, Month month, DayOfWeek dayOfWeek) {
        return LocalDate.of(year, month, 1)
                .with(TemporalAdjusters.lastInMonth(dayOfWeek));
    }

    private LocalDate goodFriday(int year) {
        LocalDate easter = easterSunday(year);
        return easter.minusDays(2);
    }

    // Meeus/Jones/Butcher Gregorian algorithm.
    private LocalDate easterSunday(int year) {
        int a = year % 19;
        int b = year / 100;
        int c = year % 100;
        int d = b / 4;
        int e = b % 4;
        int f = (b + 8) / 25;
        int g = (b - f + 1) / 3;
        int h = (19 * a + b - d - g + 15) % 30;
        int i = c / 4;
        int k = c % 4;
        int l = (32 + 2 * e + 2 * i - h - k) % 7;
        int m = (a + 11 * h + 22 * l) / 451;
        int month = (h + l - 7 * m + 114) / 31;
        int day = ((h + l - 7 * m + 114) % 31) + 1;
        return LocalDate.of(year, month, day);
    }
}
