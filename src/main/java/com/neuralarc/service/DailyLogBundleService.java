package com.neuralarc.service;

import com.neuralarc.util.AppMetadata;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

public class DailyLogBundleService {
    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter FILE_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final Path logDirectory;
    private final Clock clock;

    public DailyLogBundleService() {
        this(AppMetadata.appDataDirectory(), Clock.systemDefaultZone());
    }

    DailyLogBundleService(Path logDirectory, Clock clock) {
        this.logDirectory = logDirectory;
        this.clock = clock;
    }

    public FeedbackEmailService.SupportEmailAttachment buildTodayLogAttachment() {
        LocalDate today = LocalDate.now(clock);
        String filename = "neuralarc-logs-" + today.format(FILE_DATE) + ".txt";
        String report = buildTodayLogReport(today);
        return FeedbackEmailService.SupportEmailAttachment.textFile(filename, report);
    }

    private String buildTodayLogReport(LocalDate today) {
        StringBuilder builder = new StringBuilder();
        builder.append("NeuralArc Daily Logs").append(System.lineSeparator());
        builder.append("Date: ").append(today).append(System.lineSeparator());
        builder.append("Directory: ").append(logDirectory).append(System.lineSeparator()).append(System.lineSeparator());

        List<Path> files = listLogFiles();
        if (files.isEmpty()) {
            builder.append("No .log files found in ").append(logDirectory).append(System.lineSeparator());
            return builder.toString();
        }

        String isoDateToken = today.format(ISO_DATE);
        for (Path file : files) {
            appendFileSection(builder, file, today, isoDateToken);
        }
        return builder.toString();
    }

    private List<Path> listLogFiles() {
        if (!Files.isDirectory(logDirectory)) {
            return List.of();
        }
        try (Stream<Path> paths = Files.list(logDirectory)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".log"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString().toLowerCase(Locale.ROOT)))
                    .toList();
        } catch (IOException ignored) {
            return List.of();
        }
    }

    private void appendFileSection(StringBuilder builder, Path file, LocalDate today, String isoDateToken) {
        builder.append("===== ").append(file.getFileName()).append(" =====").append(System.lineSeparator());
        List<String> lines;
        try {
            lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            builder.append("Unable to read file: ").append(ex.getMessage()).append(System.lineSeparator()).append(System.lineSeparator());
            return;
        }

        List<String> filtered = filterForToday(file, lines, today, isoDateToken);
        if (filtered.isEmpty()) {
            builder.append("No log entries found for ").append(today).append(System.lineSeparator()).append(System.lineSeparator());
            return;
        }

        for (String line : filtered) {
            builder.append(line).append(System.lineSeparator());
        }
        builder.append(System.lineSeparator());
    }

    private List<String> filterForToday(Path file, List<String> lines, LocalDate today, String isoDateToken) {
        String fileName = file.getFileName().toString().toLowerCase(Locale.ROOT);
        if ("app.log".equals(fileName)) {
            return isModifiedToday(file, today) ? lines : List.of();
        }

        List<String> filtered = new ArrayList<>();
        for (String line : lines) {
            if (line != null && line.contains(isoDateToken)) {
                filtered.add(line);
            }
        }

        if (!filtered.isEmpty()) {
            return filtered;
        }

        return isModifiedToday(file, today) ? lines : List.of();
    }

    private boolean isModifiedToday(Path file, LocalDate today) {
        try {
            ZoneId zone = clock.getZone();
            LocalDate modified = Files.getLastModifiedTime(file).toInstant().atZone(zone).toLocalDate();
            return modified.equals(today);
        } catch (IOException ignored) {
            return false;
        }
    }
}

