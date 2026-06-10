package com.neuralarc.service;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GitHubReleaseUpdateServiceTest {

    @Test
    void compareVersionsHandlesSemanticParts() {
        assertEquals(0, GitHubReleaseUpdateService.compareVersions("1.0.0", "1.0"));
        assertEquals(1, GitHubReleaseUpdateService.compareVersions("1.0.1", "1.0.0"));
        assertEquals(-1, GitHubReleaseUpdateService.compareVersions("1.2.0", "1.10.0"));
    }

    @Test
    void normalizeVersionStripsLeadingVAndSuffix() {
        assertEquals("1.2.3", GitHubReleaseUpdateService.normalizeVersion("v1.2.3"));
        assertEquals("1.2.3", GitHubReleaseUpdateService.normalizeVersion("1.2.3-SNAPSHOT"));
    }

    @Test
    void selectAssetPrefersMacInstaller() {
        JSONArray assets = new JSONArray()
                .put(new JSONObject().put("name", "NeuralArc-1.0.1.zip").put("browser_download_url", "https://example.com/a.zip"))
                .put(new JSONObject().put("name", "NeuralArc-1.0.1.dmg").put("browser_download_url", "https://example.com/a.dmg"));

        GitHubReleaseUpdateService.AssetMatch match = GitHubReleaseUpdateService.selectAsset(
                assets, GitHubReleaseUpdateService.Platform.MAC
        );

        assertEquals("NeuralArc-1.0.1.dmg", match.assetName());
        assertEquals("https://example.com/a.dmg", match.downloadUrl());
    }

    @Test
    void selectAssetPrefersWindowsInstaller() {
        JSONArray assets = new JSONArray()
                .put(new JSONObject().put("name", "NeuralArc-1.0.1.zip").put("browser_download_url", "https://example.com/a.zip"))
                .put(new JSONObject().put("name", "NeuralArc-1.0.1.exe").put("browser_download_url", "https://example.com/a.exe"));

        GitHubReleaseUpdateService.AssetMatch match = GitHubReleaseUpdateService.selectAsset(
                assets, GitHubReleaseUpdateService.Platform.WINDOWS
        );

        assertEquals("NeuralArc-1.0.1.exe", match.assetName());
        assertEquals("https://example.com/a.exe", match.downloadUrl());
    }

    @Test
    void detectsDataCompatibilityWarningsFromReleaseNotes() {
        assertEquals(true, GitHubReleaseUpdateService.hasDataCompatibilityWarning(
                "Important: [data-incompatible] this release rebuilds local storage."
        ));
        assertEquals(true, GitHubReleaseUpdateService.hasDataCompatibilityWarning(
                "Breaking data migration. Local data may be lost in rare cases."
        ));
        assertEquals(false, GitHubReleaseUpdateService.hasDataCompatibilityWarning(
                "Bug fixes and UI improvements."
        ));
    }
}
