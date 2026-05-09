package com.neuralarc.service;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.net.URI;
import java.nio.file.Path;

public final class SpacesLogUploader implements AutoCloseable {
    private final LogUploadConfig config;
    private final S3Client s3Client;

    public SpacesLogUploader(LogUploadConfig config) {
        this.config = config;
        this.s3Client = S3Client.builder()
                .endpointOverride(URI.create(config.endpoint()))
                .region(Region.of(config.region()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(config.accessKey(), config.secretKey())
                ))
                .forcePathStyle(false)
                .build();
    }

    public boolean isConfigured() {
        return config.enabled()
                && !blank(config.endpoint())
                && !blank(config.region())
                && !blank(config.bucket())
                && !blank(config.accessKey())
                && !blank(config.secretKey());
    }

    public void ensureEmailFile(String userId, String email) {
        String key = userId + "/email.txt";
        try {
            s3Client.headObject(HeadObjectRequest.builder().bucket(config.bucket()).key(key).build());
            return;
        } catch (NoSuchKeyException ignored) {
            // Upload below.
        } catch (Exception ignored) {
            // A missing object can also surface as a generic S3 exception depending on provider response.
        }
        s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(config.bucket())
                        .key(key)
                        .contentType("text/plain")
                        .build(),
                RequestBody.fromString(email == null ? "" : email)
        );
    }

    public void uploadArchive(String remoteObjectKey, Path archivePath) {
        s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(config.bucket())
                        .key(remoteObjectKey)
                        .contentType("application/zip")
                        .build(),
                RequestBody.fromFile(archivePath)
        );
    }

    @Override
    public void close() {
        s3Client.close();
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    public record LogUploadConfig(
            boolean enabled,
            String endpoint,
            String region,
            String bucket,
            String accessKey,
            String secretKey
    ) {
    }
}
