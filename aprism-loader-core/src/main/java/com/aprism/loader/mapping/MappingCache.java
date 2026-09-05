package com.aprism.loader.mapping;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Content-addressed mapping asset cache (v26.9-Alpha.2).
 *
 * <p>Assets are stored under {@code <cacheDir>/<logicalName>/<sha256hex>} with a
 * {@code latest.txt} pointer. Every read re-verifies the SHA-256 of the stored
 * bytes and fails closed on mismatch, so a corrupted or truncated cache entry
 * can never be served as a valid mapping asset.
 *
 * @author BlockConnect@StarsailsClover
 */
public final class MappingCache {
    //GitHub@NDBlockConnect | BlockConnect@StarsailsClover

    /** A cache entry after successful integrity verification. */
    public record CachedAsset(String logicalName, Path path, String sha256, long size) {
    }

    private final Path cacheDir;

    public MappingCache(Path cacheDir) throws IOException {
        this.cacheDir = cacheDir;
        Files.createDirectories(cacheDir);
    }

    /**
     * Stores content under the logical name and returns the verified entry.
     * The write is atomic (temp file + move) so a crashed fetch cannot leave
     * a half-written latest asset behind.
     */
    public CachedAsset put(String logicalName, byte[] content) throws IOException {
        String sha256 = sha256Hex(content);
        Path dir = cacheDir.resolve(logicalName);
        Files.createDirectories(dir);
        Path finalPath = dir.resolve(sha256);
        if (!Files.exists(finalPath)) {
            Path tmp = dir.resolve(sha256 + ".tmp-" + ProcessHandle.current().pid());
            Files.write(tmp, content);
            try {
                Files.move(tmp, finalPath, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicUnsupported) {
                Files.move(tmp, finalPath, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        Files.writeString(dir.resolve("latest.txt"), sha256, StandardCharsets.UTF_8);
        return new CachedAsset(logicalName, finalPath, sha256, content.length);
    }

    /**
     * Reads the latest entry for the logical name, re-verifying its SHA-256.
     *
     * @return the verified asset, or null when the logical name is unknown
     * @throws IOException on read failure or integrity mismatch (fail-closed)
     */
    public CachedAsset get(String logicalName) throws IOException {
        Path dir = cacheDir.resolve(logicalName);
        Path latest = dir.resolve("latest.txt");
        if (!Files.isRegularFile(latest)) {
            return null;
        }
        String expected = Files.readString(latest, StandardCharsets.UTF_8).trim();
        Path file = dir.resolve(expected);
        if (!Files.isRegularFile(file)) {
            throw new IOException("cache entry missing: " + logicalName
                    + "/" + expected);
        }
        byte[] content = Files.readAllBytes(file);
        String actual = sha256Hex(content);
        if (!actual.equals(expected)) {
            throw new IOException("cache integrity failure for " + logicalName
                    + ": expected sha256 " + expected + " but read " + actual);
        }
        return new CachedAsset(logicalName, file, actual, content.length);
    }

    /**
     * @return true when the latest cached entry for the logical name exists
     *         and its bytes hash to the expected SHA-256
     */
    public boolean verify(String logicalName, String expectedSha256) {
        try {
            CachedAsset asset = get(logicalName);
            return asset != null && asset.sha256().equals(expectedSha256);
        } catch (IOException integrityFailure) {
            return false;
        }
    }

    /**
     * @return the SHA-256 hex of the content
     */
    public static String sha256Hex(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }
}
