package com.neuralarc.service;

import com.neuralarc.service.LogUploadStatus.UploadState;

import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public final class AsyncLogUploadService implements AutoCloseable {
    private final LogArchiveService archiveService;
    private final LogUploadStatusStore statusStore;
    private final SpacesLogUploader uploader;
    private final ScheduledExecutorService executor;
    private final String userId;
    private final String userEmail;
    private final LocalTime marketCloseUploadTime;
    private final int maxRetryCount;
    private final Duration retryBackoff;
    private final Consumer<String> logger;

    public AsyncLogUploadService(
            LogArchiveService archiveService,
            LogUploadStatusStore statusStore,
            SpacesLogUploader uploader,
            String userId,
            String userEmail,
            LocalTime marketCloseUploadTime,
            int maxRetryCount,
            Duration retryBackoff,
            Consumer<String> logger
    ) {
        this.archiveService = archiveService;
        this.statusStore = statusStore;
        this.uploader = uploader;
        this.userId = sanitize(userId);
        this.userEmail = userEmail == null ? "" : userEmail.trim();
        this.marketCloseUploadTime = marketCloseUploadTime == null ? LocalTime.of(16, 15) : marketCloseUploadTime;
        this.maxRetryCount = Math.max(0, maxRetryCount);
        this.retryBackoff = retryBackoff == null ? Duration.ofMinutes(30) : retryBackoff;
        this.logger = logger == null ? ignored -> {} : logger;
        this.executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "neuralarc-log-uploader");
            thread.setDaemon(true);
            return thread;
        });
    }

    public void start() {
        if (!uploader.isConfigured()) {
            logger.accept("[LOG-UPLOAD] Disabled or incomplete DigitalOcean Spaces configuration.");
            return;
        }
        executor.execute(this::safeReconcileAndUpload);
        scheduleNextMarketCloseUpload();
    }

    private void scheduleNextMarketCloseUpload() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime next = LocalDateTime.of(now.toLocalDate(), marketCloseUploadTime);
        if (!next.isAfter(now)) {
            next = next.plusDays(1);
        }
        long delayMillis = Math.max(0L, next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                - System.currentTimeMillis());
        executor.schedule(() -> {
            safeReconcileAndUpload();
            scheduleNextMarketCloseUpload();
        }, delayMillis, TimeUnit.MILLISECONDS);
    }

    private void safeReconcileAndUpload() {
        try {
            uploader.ensureEmailFile(userId, userEmail);
            LocalDate today = LocalDate.now();
            LocalDate beforeDate = LocalTime.now().isBefore(marketCloseUploadTime)
                    ? today
                    : today.plusDays(1);
            List<LocalDate> dates = archiveService.discoverArchivedLogDates(beforeDate);
            for (LocalDate date : dates) {
                if (statusStore.isUploaded(date)) {
                    continue;
                }
                uploadDate(date);
            }
        } catch (Exception ex) {
            logger.accept("[LOG-UPLOAD] Reconciliation failed: " + ex.getMessage());
            scheduleRetry();
        }
    }

    private void uploadDate(LocalDate date) {
        LogUploadStatus existing = statusStore.find(date).orElse(null);
        int retryCount = existing == null ? 0 : existing.retryCount();
        if (retryCount >= maxRetryCount && existing != null && existing.uploadStatus() == UploadState.FAILED_PERMANENT) {
            return;
        }
        try {
            Path archive = archiveService.archive(date);
            String remoteKey = userId + "/logs/" + date + "/" + archive.getFileName();
            statusStore.save(new LogUploadStatus(date, archive.getFileName().toString(), UploadState.UPLOADING, null,
                    retryCount, "", remoteKey));
            logger.accept("[LOG-UPLOAD] Uploading " + archive.getFileName() + " to " + remoteKey);
            uploader.uploadArchive(remoteKey, archive);
            statusStore.save(new LogUploadStatus(date, archive.getFileName().toString(), UploadState.UPLOADED,
                    java.time.Instant.now(), retryCount, "", remoteKey));
            logger.accept("[LOG-UPLOAD] Uploaded " + archive.getFileName());
        } catch (Exception ex) {
            int nextRetry = retryCount + 1;
            UploadState state = nextRetry >= maxRetryCount ? UploadState.FAILED_PERMANENT : UploadState.FAILED_RETRYABLE;
            statusStore.save(new LogUploadStatus(date, "logs-" + date + ".zip", state, null,
                    nextRetry, ex.getMessage(), userId + "/logs/" + date + "/logs-" + date + ".zip"));
            logger.accept("[LOG-UPLOAD] Upload failed for " + date + ": " + ex.getMessage());
            if (state == UploadState.FAILED_RETRYABLE) {
                scheduleRetry();
            }
        }
    }

    private void scheduleRetry() {
        executor.schedule(this::safeReconcileAndUpload, retryBackoff.toMillis(), TimeUnit.MILLISECONDS);
    }

    private String sanitize(String value) {
        String sanitized = value == null ? "" : value.replaceAll("[^A-Za-z0-9._-]", "_");
        return sanitized.isBlank() ? "unknown-user" : sanitized;
    }

    @Override
    public void close() {
        executor.shutdownNow();
        uploader.close();
    }
}
