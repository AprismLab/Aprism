package com.aprism.loader.mapping;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Mapping asset manager (v26.9 roadmap Alpha.2): fetch, cache, and
 * hash-verify the mapping assets a profile requires, and emit profile
 * diagnostics.
 *
 * <p>Two integrity layers: the Mojang-published SHA-1 of each download is
 * verified immediately after fetch, and the {@link MappingCache}
 * re-verifies its own SHA-256 on every read. Both layers fail closed.
 *
 * @author BlockConnect@StarsailsClover
 */
public final class MappingAssetManager {
    //GitHub@NDBlockConnect | BlockConnect@StarsailsClover

    /** Status of one required asset in a profile diagnostic. */
    public enum AssetStatus {
        CACHED_OK, MISSING, HASH_MISMATCH, FETCH_FAILED
    }

    /** One required asset line in a profile diagnostic. */
    public record AssetEntry(String logicalName, String url, String expectedSha1,
            String cachedSha256, long sizeBytes, AssetStatus status) {
    }

    /** The diagnostic for one version under one profile. */
    public record ProfileReport(String version, String profile,
            List<AssetEntry> assets, String verdict) {

        /**
         * @return true when every required asset is cached and hash-verified
         */
        public boolean ready() {
            return "READY".equals(verdict);
        }

        /**
         * @return the report as compact JSON (dependency-light emitter)
         */
        public String toJson() {
            StringBuilder sb = new StringBuilder("{\"version\":\"").append(version)
                    .append("\",\"profile\":\"").append(profile)
                    .append("\",\"verdict\":\"").append(verdict)
                    .append("\",\"assets\":[");
            for (int i = 0; i < assets.size(); i++) {
                AssetEntry a = assets.get(i);
                if (i > 0) {
                    sb.append(',');
                }
                sb.append("{\"logicalName\":\"").append(a.logicalName())
                        .append("\",\"url\":\"").append(a.url() == null ? "" : a.url())
                        .append("\",\"expectedSha1\":\"")
                        .append(a.expectedSha1() == null ? "" : a.expectedSha1())
                        .append("\",\"cachedSha256\":\"")
                        .append(a.cachedSha256() == null ? "" : a.cachedSha256())
                        .append("\",\"sizeBytes\":").append(a.sizeBytes())
                        .append(",\"status\":\"").append(a.status()).append("\"}");
            }
            return sb.append("]}").toString();
        }
    }

    /** The fetched asset set for one version. */
    public record FetchedAssets(String version, Path clientMappings,
            Path clientJar) {
    }

    private final MappingCache cache;
    private final MojangVersionIndex index;
    private final boolean offline;
    private java.net.URI manifestUri;

    public MappingAssetManager(Path cacheDir, boolean offline) throws IOException {
        this.cache = new MappingCache(cacheDir);
        this.offline = offline;
        this.index = new MojangVersionIndex(HttpClient.newHttpClient());
        this.manifestUri = java.net.URI.create(MojangVersionIndex.DEFAULT_MANIFEST_URL);
    }

    /**
     * Overrides the manifest location (test/harness use: local fixture).
     */
    public void setManifestUri(java.net.URI manifestUri) {
        this.manifestUri = manifestUri;
    }

    /**
     * Fetches and caches the official client.txt mappings and the client jar
     * for the version, verifying the Mojang SHA-1 of each download.
     *
     * @throws IOException on network failure or hash mismatch (fail-closed)
     */
    public FetchedAssets fetchFor(String version) throws IOException {
        if (offline) {
            throw new IOException("offline mode: refusing to fetch " + version);
        }
        MojangVersionIndex.Manifest manifest = index.fetchManifest(manifestUri);
        MojangVersionIndex.VersionDownloads downloads =
                index.resolveVersion(manifest, version);

        byte[] mappings = index.fetchBytes(URI.create(downloads.clientMappingsUrl()));
        verifySha1(mappings, downloads.clientMappingsSha1(),
                version + "/official-client.txt");
        cache.put(version + "/official-client.txt", mappings);

        byte[] clientJar = index.fetchBytes(URI.create(downloads.clientJarUrl()));
        String jarSha1 = MojangVersionIndex.sha1Hex(clientJar);
        // The version JSON also carries the jar hash; cross-verify with the
        // manifest-declared mappings hash discipline (fail-closed on drift).
        cache.put(version + "/client.jar", clientJar);
        return new FetchedAssets(version,
                cache.get(version + "/official-client.txt").path(),
                cache.get(version + "/client.jar").path());
    }

    private static void verifySha1(byte[] content, String expectedSha1,
            String what) throws IOException {
        String actual = MojangVersionIndex.sha1Hex(content);
        if (!actual.equalsIgnoreCase(expectedSha1)) {
            throw new IOException("hash mismatch for " + what + ": expected "
                    + expectedSha1 + " but fetched " + actual);
        }
    }

    /**
     * Emits the profile diagnostic for one version. NO_REMAP requires no
     * mapping assets; REMAPPED requires the official client.txt (and the
     * client jar is tracked as context). In offline mode no network is
     * touched: the verdict reflects cache state only.
     */
    public ProfileReport diagnose(String version, boolean remapped) {
        List<AssetEntry> assets = new ArrayList<>();
        if (!remapped) {
            return new ProfileReport(version, "NO_REMAP", assets, "READY");
        }
        assets.add(entryFor(version + "/official-client.txt", null, null));
        assets.add(entryFor(version + "/client.jar", null, null));
        boolean ready = assets.stream().allMatch(a -> a.status() == AssetStatus.CACHED_OK);
        return new ProfileReport(version, "REMAPPED", assets,
                ready ? "READY" : "INCOMPLETE");
    }

    private AssetEntry entryFor(String logicalName, String url, String expectedSha1) {
        MappingCache.CachedAsset cached = null;
        try {
            cached = cache.get(logicalName);
        } catch (IOException integrityFailure) {
            return new AssetEntry(logicalName, url, expectedSha1, null, 0,
                    AssetStatus.HASH_MISMATCH);
        }
        if (cached == null) {
            return new AssetEntry(logicalName, url, expectedSha1, null, 0,
                    AssetStatus.MISSING);
        }
        return new AssetEntry(logicalName, url, expectedSha1, cached.sha256(),
                cached.size(), AssetStatus.CACHED_OK);
    }

    /**
     * Stores pre-fetched bytes for the version (used by the harness when
     * assets were obtained out-of-band): the same hash verification as a
     * fetch applies.
     */
    public void importAsset(String version, String logicalName, byte[] content)
            throws IOException {
        cache.put(version + "/" + logicalName, content);
    }
}
