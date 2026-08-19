package com.neuralarc.service;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;

public class GitHubReleaseUpdateService {
    private static final String USER_AGENT = "NeuralArc-Desktop";

    private final HttpClient httpClient;
    private final String latestReleaseUrl;

    public GitHubReleaseUpdateService(String latestReleaseUrl) {
        this(HttpClient.newHttpClient(), latestReleaseUrl);
    }

    GitHubReleaseUpdateService(HttpClient httpClient, String latestReleaseUrl) {
        this.httpClient = httpClient;
        this.latestReleaseUrl = latestReleaseUrl == null ? "" : latestReleaseUrl.trim();
    }

    public boolean isConfigured() {
        return !latestReleaseUrl.isBlank();
    }

    public String missingConfigMessage() {
        return "Missing GitHub release URL. Set app.update.github.latestReleaseUrl in app.properties.";
    }

    public UpdateCheckResult checkForUpdates(String currentVersion) throws IOException, InterruptedException {
        if (!isConfigured()) {
            return UpdateCheckResult.notConfigured(currentVersion, missingConfigMessage());
        }

        HttpRequest request = HttpRequest.newBuilder(URI.create(latestReleaseUrl))
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", USER_AGENT)
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("GitHub release check failed with status " + response.statusCode());
        }

        JSONObject json = new JSONObject(response.body());
        String latestVersion = normalizeVersion(json.optString("tag_name", json.optString("name", "")));
        String normalizedCurrentVersion = normalizeVersion(currentVersion);
        boolean developmentBuild = isDevelopmentVersion(currentVersion);
        String releaseName = json.optString("name", latestVersion);
        String releaseNotes = json.optString("body", "");
        String releasePageUrl = json.optString("html_url", "");
        String publishedAt = json.optString("published_at", "");
        AssetMatch asset = selectAsset(json.optJSONArray("assets"), detectPlatform());
        boolean updateAvailable = !developmentBuild
                && compareVersions(latestVersion, normalizedCurrentVersion) > 0;
        boolean dataCompatibilityWarning = hasDataCompatibilityWarning(releaseNotes);

        return new UpdateCheckResult(
                true,
                updateAvailable,
                normalizedCurrentVersion,
                latestVersion,
                releaseName,
                releaseNotes,
                releasePageUrl,
                asset.downloadUrl(),
                asset.assetName(),
                publishedAt.isBlank() ? null : Instant.parse(publishedAt),
                dataCompatibilityWarning,
                developmentBuild
                        ? "Development build detected; update notice is suppressed."
                        : (updateAvailable
                        ? "Update available"
                        : "You are already on the latest version.")
        );
    }

    static Platform detectPlatform() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("mac")) {
            return Platform.MAC;
        }
        if (os.contains("win")) {
            return Platform.WINDOWS;
        }
        return Platform.OTHER;
    }

    static AssetMatch selectAsset(JSONArray assets, Platform platform) {
        if (assets == null || assets.isEmpty()) {
            return new AssetMatch("", "");
        }

        String[] preferredExtensions = switch (platform) {
            case MAC -> new String[]{".dmg", ".pkg", ".zip"};
            case WINDOWS -> new String[]{".exe", ".msi", ".zip"};
            case OTHER -> new String[]{".zip", ".tar.gz", ".tgz"};
        };

        for (String extension : preferredExtensions) {
            for (int i = 0; i < assets.length(); i++) {
                JSONObject asset = assets.optJSONObject(i);
                if (asset == null) {
                    continue;
                }
                String name = asset.optString("name", "");
                if (name.toLowerCase().endsWith(extension)) {
                    return new AssetMatch(name, asset.optString("browser_download_url", ""));
                }
            }
        }

        JSONObject first = assets.optJSONObject(0);
        if (first == null) {
            return new AssetMatch("", "");
        }
        return new AssetMatch(first.optString("name", ""), first.optString("browser_download_url", ""));
    }

    static String normalizeVersion(String raw) {
        if (raw == null) {
            return "";
        }
        String trimmed = raw.trim();
        if (trimmed.startsWith("v") || trimmed.startsWith("V")) {
            trimmed = trimmed.substring(1);
        }
        int cut = 0;
        while (cut < trimmed.length()) {
            char ch = trimmed.charAt(cut);
            if (!Character.isDigit(ch) && ch != '.') {
                break;
            }
            cut++;
        }
        return cut == 0 ? trimmed : trimmed.substring(0, cut);
    }

    static boolean isDevelopmentVersion(String raw) {
        if (raw == null || raw.isBlank()) {
            return true;
        }
        String normalized = raw.trim().toLowerCase();
        return normalized.contains("snapshot")
                || normalized.equals("dev")
                || normalized.endsWith("-dev")
                || normalized.startsWith("dev-");
    }

    static int compareVersions(String left, String right) {
        String[] leftParts = normalizeVersion(left).split("\\.");
        String[] rightParts = normalizeVersion(right).split("\\.");
        int count = Math.max(leftParts.length, rightParts.length);
        for (int i = 0; i < count; i++) {
            int leftValue = i < leftParts.length && !leftParts[i].isBlank() ? Integer.parseInt(leftParts[i]) : 0;
            int rightValue = i < rightParts.length && !rightParts[i].isBlank() ? Integer.parseInt(rightParts[i]) : 0;
            if (leftValue != rightValue) {
                return Integer.compare(leftValue, rightValue);
            }
        }
        return 0;
    }

    static boolean hasDataCompatibilityWarning(String releaseNotes) {
        if (releaseNotes == null || releaseNotes.isBlank()) {
            return false;
        }
        String normalized = releaseNotes.toLowerCase();
        return normalized.contains("[data-incompatible]")
                || normalized.contains("[local-data-reset]")
                || normalized.contains("local data may be lost")
                || normalized.contains("data loss")
                || normalized.contains("incompatible with local data")
                || normalized.contains("incompatible with existing data")
                || normalized.contains("breaking data migration");
    }

    enum Platform {
        MAC,
        WINDOWS,
        OTHER
    }

    record AssetMatch(String assetName, String downloadUrl) {
    }

    public record UpdateCheckResult(
            boolean configured,
            boolean updateAvailable,
            String currentVersion,
            String latestVersion,
            String releaseName,
            String releaseNotes,
            String releasePageUrl,
            String downloadUrl,
            String assetName,
            Instant publishedAt,
            boolean dataCompatibilityWarning,
            String message
    ) {
        static UpdateCheckResult notConfigured(String currentVersion, String message) {
            return new UpdateCheckResult(false, false, normalizeVersion(currentVersion), "", "", "", "", "", "", null, false, message);
        }
    }
}
