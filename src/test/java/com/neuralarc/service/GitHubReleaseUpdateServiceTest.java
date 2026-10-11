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
    void detectsDevelopmentVersionsForIdeRuns() {
        assertEquals(true, GitHubReleaseUpdateService.isDevelopmentVersion("1.0.0-SNAPSHOT"));
        assertEquals(true, GitHubReleaseUpdateService.isDevelopmentVersion("dev"));
        assertEquals(false, GitHubReleaseUpdateService.isDevelopmentVersion("1.0.70"));
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

    private static JSONArray macRelease() {
        return new JSONArray()
                .put(new JSONObject().put("name", "NeuralArc-1.2.3-x64.dmg").put("browser_download_url", "https://example.com/x64.dmg"))
                .put(new JSONObject().put("name", "NeuralArc-1.2.3-arm64.dmg").put("browser_download_url", "https://example.com/arm64.dmg"))
                .put(new JSONObject().put("name", "NeuralArc-1.2.3.exe").put("browser_download_url", "https://example.com/a.exe"));
    }

    @Test
    void selectAssetPicksTheMacInstallerBuiltForThisCpu() {
        assertEquals("NeuralArc-1.2.3-arm64.dmg", GitHubReleaseUpdateService.selectAsset(
                macRelease(), GitHubReleaseUpdateService.Platform.MAC, GitHubReleaseUpdateService.Arch.ARM64).assetName());
        assertEquals("NeuralArc-1.2.3-x64.dmg", GitHubReleaseUpdateService.selectAsset(
                macRelease(), GitHubReleaseUpdateService.Platform.MAC, GitHubReleaseUpdateService.Arch.X64).assetName());
    }

    @Test
    void selectAssetFallsBackToAnUntaggedDmgFromAnOlderRelease() {
        JSONArray legacy = new JSONArray()
                .put(new JSONObject().put("name", "NeuralArc-1.0.1.dmg").put("browser_download_url", "https://example.com/a.dmg"));

        assertEquals("NeuralArc-1.0.1.dmg", GitHubReleaseUpdateService.selectAsset(
                legacy, GitHubReleaseUpdateService.Platform.MAC, GitHubReleaseUpdateService.Arch.ARM64).assetName());
    }

    @Test
    void selectAssetNeverOffersAnInstallerBuiltForTheOtherCpu() {
        JSONArray intelOnly = new JSONArray()
                .put(new JSONObject().put("name", "NeuralArc-1.2.3-x64.dmg").put("browser_download_url", "https://example.com/x64.dmg"));

        GitHubReleaseUpdateService.AssetMatch match = GitHubReleaseUpdateService.selectAsset(
                intelOnly, GitHubReleaseUpdateService.Platform.MAC, GitHubReleaseUpdateService.Arch.ARM64);

        assertEquals("", match.downloadUrl(), "the caller then falls back to the release page");
    }

    @Test
    void selectAssetWithAnUnknownCpuStillReturnsAMacInstaller() {
        assertEquals("NeuralArc-1.2.3-x64.dmg", GitHubReleaseUpdateService.selectAsset(
                macRelease(), GitHubReleaseUpdateService.Platform.MAC, GitHubReleaseUpdateService.Arch.UNKNOWN).assetName());
    }

    @Test
    void readsTheCpuFromTheJvmArchitectureAndTheAssetName() {
        assertEquals(GitHubReleaseUpdateService.Arch.ARM64, GitHubReleaseUpdateService.archOf("aarch64"));
        assertEquals(GitHubReleaseUpdateService.Arch.X64, GitHubReleaseUpdateService.archOf("x86_64"));
        assertEquals(GitHubReleaseUpdateService.Arch.X64, GitHubReleaseUpdateService.archOf("amd64"));
        assertEquals(GitHubReleaseUpdateService.Arch.UNKNOWN, GitHubReleaseUpdateService.archOf("riscv64"));
        assertEquals(GitHubReleaseUpdateService.Arch.ARM64, GitHubReleaseUpdateService.assetArch("NeuralArc-1.2.3-arm64.dmg"));
        assertEquals(GitHubReleaseUpdateService.Arch.X64, GitHubReleaseUpdateService.assetArch("NeuralArc-1.2.3-x64.dmg"));
        assertEquals(GitHubReleaseUpdateService.Arch.UNKNOWN, GitHubReleaseUpdateService.assetArch("NeuralArc-1.2.3.dmg"));
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
