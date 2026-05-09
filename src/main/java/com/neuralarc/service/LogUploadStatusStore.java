package com.neuralarc.service;

import org.json.JSONObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class LogUploadStatusStore {
    private final Path statusFile;
    private final Map<LocalDate, LogUploadStatus> statuses = new LinkedHashMap<>();

    public LogUploadStatusStore(Path statusFile) {
        this.statusFile = statusFile;
        load();
    }

    public synchronized Optional<LogUploadStatus> find(LocalDate date) {
        return Optional.ofNullable(statuses.get(date));
    }

    public synchronized boolean isUploaded(LocalDate date) {
        return find(date)
                .map(status -> status.uploadStatus() == LogUploadStatus.UploadState.UPLOADED)
                .orElse(false);
    }

    public synchronized void save(LogUploadStatus status) {
        statuses.put(status.logDate(), status);
        flush();
    }

    private void load() {
        if (!Files.exists(statusFile)) {
            return;
        }
        try {
            JSONObject root = new JSONObject(Files.readString(statusFile));
            for (String key : root.keySet()) {
                JSONObject item = root.getJSONObject(key);
                LocalDate date = LocalDate.parse(key);
                statuses.put(date, new LogUploadStatus(
                        date,
                        item.optString("archiveFileName", ""),
                        LogUploadStatus.UploadState.valueOf(item.optString("uploadStatus", "PENDING")),
                        item.optString("uploadTimestamp", "").isBlank() ? null : Instant.parse(item.getString("uploadTimestamp")),
                        item.optInt("retryCount", 0),
                        item.optString("lastErrorMessage", ""),
                        item.optString("remoteObjectKey", "")
                ));
            }
        } catch (Exception ignored) {
            statuses.clear();
        }
    }

    private void flush() {
        try {
            Files.createDirectories(statusFile.getParent());
            JSONObject root = new JSONObject();
            for (LogUploadStatus status : statuses.values()) {
                JSONObject item = new JSONObject();
                item.put("archiveFileName", status.archiveFileName());
                item.put("uploadStatus", status.uploadStatus().name());
                item.put("uploadTimestamp", status.uploadTimestamp() == null ? "" : status.uploadTimestamp().toString());
                item.put("retryCount", status.retryCount());
                item.put("lastErrorMessage", status.lastErrorMessage() == null ? "" : status.lastErrorMessage());
                item.put("remoteObjectKey", status.remoteObjectKey() == null ? "" : status.remoteObjectKey());
                root.put(status.logDate().toString(), item);
            }
            Files.writeString(statusFile, root.toString(2));
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to persist log upload status", ex);
        }
    }
}
