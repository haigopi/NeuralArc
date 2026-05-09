package com.neuralarc.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class LogArchiveService {
    private final Path logDirectory;
    private final Path archiveDirectory;

    public LogArchiveService(Path logDirectory, Path archiveDirectory) {
        this.logDirectory = logDirectory;
        this.archiveDirectory = archiveDirectory;
    }

    public List<LocalDate> discoverArchivedLogDates(LocalDate beforeDate) throws IOException {
        if (!Files.isDirectory(logDirectory)) {
            return List.of();
        }
        List<LocalDate> dates = new ArrayList<>();
        try (var stream = Files.list(logDirectory)) {
            stream.map(path -> dateFromLogName(path.getFileName().toString()))
                    .filter(date -> date != null && date.isBefore(beforeDate))
                    .distinct()
                    .sorted()
                    .forEach(dates::add);
        }
        return dates;
    }

    public Path archive(LocalDate date) throws IOException {
        Files.createDirectories(archiveDirectory);
        Path archive = archiveDirectory.resolve("logs-" + date + ".zip");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archive))) {
            for (RotatingLogWriter.LogType type : RotatingLogWriter.LogType.values()) {
                Path logFile = logDirectory.resolve(type.name().toLowerCase() + "-" + date + ".log");
                if (!Files.exists(logFile)) {
                    continue;
                }
                zip.putNextEntry(new ZipEntry(logFile.getFileName().toString()));
                Files.copy(logFile, zip);
                zip.closeEntry();
            }
        }
        return archive;
    }

    static LocalDate dateFromLogName(String fileName) {
        if (fileName == null || !fileName.endsWith(".log")) {
            return null;
        }
        int dash = fileName.indexOf('-');
        if (dash < 0) {
            return null;
        }
        try {
            return LocalDate.parse(fileName.substring(dash + 1, fileName.length() - 4));
        } catch (Exception ignored) {
            return null;
        }
    }
}
