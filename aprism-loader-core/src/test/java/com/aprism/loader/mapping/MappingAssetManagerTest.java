package com.aprism.loader.mapping;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
// GitHub@NDBlockConnect | BlockConnect@StarsailsClover

/**
 * Mapping asset manager tests against a local fixture HTTP server: fetch +
 * Mojang SHA-1 verification, cache integrity (fail-closed), offline
 * diagnostics, and hash-mismatch refusal. No external network.
 *
 * @author BlockConnect@StarsailsClover
 */
class MappingAssetManagerTest {

    private static final String VERSION = "99.99";
    private static final byte[] MAPPINGS_BYTES =
            "net.minecraft.world.level.block.BlockPos -> ji:\n".getBytes(StandardCharsets.UTF_8);
    private static final byte[] JAR_BYTES = "fake-client-jar-bytes".getBytes(StandardCharsets.UTF_8);

    private com.sun.net.httpserver.HttpServer server;
    private String baseUri;
    private String mappingsSha1;
    private String jarSha1;
    private boolean corruptMappings;

    @TempDir
    Path tempDir;

    @BeforeEach
    void startServer() throws IOException {
        mappingsSha1 = MojangVersionIndex.sha1Hex(MAPPINGS_BYTES);
        jarSha1 = MojangVersionIndex.sha1Hex(JAR_BYTES);
        corruptMappings = false;
        server = com.sun.net.httpserver.HttpServer.create(
                new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            byte[] body;
            String path = exchange.getRequestURI().getPath();
            if (path.equals("/manifest.json")) {
                body = manifestJson().getBytes(StandardCharsets.UTF_8);
            } else if (path.equals("/version.json")) {
                body = versionJson().getBytes(StandardCharsets.UTF_8);
            } else if (path.equals("/client.txt")) {
                byte[] source = corruptMappings
                        ? "TAMPERED".getBytes(StandardCharsets.UTF_8)
                        : MAPPINGS_BYTES;
                body = source;
            } else if (path.equals("/client.jar")) {
                body = JAR_BYTES;
            } else {
                body = new byte[0];
            }
            exchange.getResponseHeaders().add("Content-Type",
                    "application/octet-stream");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        server.setExecutor(Executors.newSingleThreadExecutor());
        server.start();
        baseUri = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private String manifestJson() {
        return "{\"latest\":{\"release\":\"" + VERSION + "\"},\"versions\":"
                + "[{\"id\":\"" + VERSION + "\",\"url\":\"" + baseUri
                + "/version.json\"}]}";
    }

    private String versionJson() {
        return "{\"downloads\":{\"client\":{\"url\":\"" + baseUri
                + "/client.jar\",\"sha1\":\"" + jarSha1 + "\",\"size\":"
                + JAR_BYTES.length + "},\"client_mappings\":{\"url\":\""
                + baseUri + "/client.txt\",\"sha1\":\"" + mappingsSha1
                + "\",\"size\":" + MAPPINGS_BYTES.length + "}}}";
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private MappingAssetManager manager(boolean offline) throws IOException {
        MappingAssetManager manager = new MappingAssetManager(
                tempDir.resolve("cache"), offline);
        manager.setManifestUri(java.net.URI.create(baseUri + "/manifest.json"));
        return manager;
    }

    @Test
    void fetchVerifiesHashesAndPopulatesCache() throws IOException {
        MappingAssetManager manager = manager(false);
        manager.fetchFor(VERSION);
        MappingAssetManager.ProfileReport report = manager.diagnose(VERSION, true);
        assertTrue(report.ready(), "verdict=" + report.verdict());
        assertEquals(2, report.assets().size());
        assertTrue(report.assets().stream().allMatch(
                a -> a.status() == MappingAssetManager.AssetStatus.CACHED_OK));
        assertNotNull(report.assets().get(0).cachedSha256());
    }

    @Test
    void fetchFailsClosedOnHashMismatch() {
        corruptMappings = true;
        assertThrows(IOException.class, () -> manager(false).fetchFor(VERSION));
    }

    @Test
    void offlineDiagnoseReflectsCacheOnly() throws IOException {
        manager(false).fetchFor(VERSION);
        MappingAssetManager offline = manager(true);
        assertTrue(offline.diagnose(VERSION, true).ready());
        MappingAssetManager.ProfileReport missing = offline.diagnose("0.0", true);
        assertFalse(missing.ready());
        assertEquals(MappingAssetManager.AssetStatus.MISSING,
                missing.assets().get(0).status());
    }

    @Test
    void corruptedCacheEntryFailsClosed() throws IOException {
        MappingAssetManager manager = manager(false);
        manager.fetchFor(VERSION);
        Path cacheRoot = tempDir.resolve("cache").resolve(VERSION
                + "/official-client.txt");
        try (var files = Files.list(cacheRoot)) {
            files.filter(p -> p.getFileName().toString().endsWith(".txt")
                            || p.getFileName().toString().length() == 64)
                    .forEach(p -> {
                        try {
                            Files.write(p, "CORRUPTED".getBytes(StandardCharsets.UTF_8));
                        } catch (IOException ignored) {
                            // test failure surfaces via the assertion below
                        }
                    });
        }
        assertFalse(manager.diagnose(VERSION, true).ready());
    }

    @Test
    void noRemapProfileRequiresNothing() throws IOException {
        MappingAssetManager manager = new MappingAssetManager(
                tempDir.resolve("cache"), true);
        assertTrue(manager.diagnose(VERSION, false).ready());
    }

    @Test
    void cacheRoundTripAndVerification() throws IOException {
        MappingCache cache = new MappingCache(tempDir.resolve("c2"));
        byte[] content = "asset-body".getBytes(StandardCharsets.UTF_8);
        MappingCache.CachedAsset put = cache.put("test/asset", content);
        assertEquals(MappingCache.sha256Hex(content), put.sha256());
        assertTrue(cache.verify("test/asset", put.sha256()));
        assertFalse(cache.verify("test/asset", "deadbeef"));
        assertNull(cache.get("test/unknown"));
    }
}
