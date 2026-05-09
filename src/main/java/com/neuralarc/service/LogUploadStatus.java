package com.neuralarc.service;

import java.time.Instant;
import java.time.LocalDate;

public record LogUploadStatus(
        LocalDate logDate,
        String archiveFileName,
        UploadState uploadStatus,
        Instant uploadTimestamp,
        int retryCount,
        String lastErrorMessage,
        String remoteObjectKey
) {
    public enum UploadState {
        PENDING,
        UPLOADING,
        UPLOADED,
        FAILED_RETRYABLE,
        FAILED_PERMANENT
    }
}
