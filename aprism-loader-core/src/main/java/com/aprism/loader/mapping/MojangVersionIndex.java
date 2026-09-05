package com.aprism.loader.mapping;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;

/**
 * Mojang piston-meta version index and per-version download resolution
 * (v26.9-Alpha.2). Parses the published version manifest v2 and extracts
 * the authoritative URLs and SHA-1 hashes for the client jar and the
 * official (client.txt) mappings.
 *
 * @author BlockConnect@StarsailsClover
 */
public final class MojangVersionIndex {
    //GitHub@NDBlockConnect | BlockConnect@StarsailsClover

    /** The authoritative download set for one Minecraft version. */
    public record VersionDownloads(String version, String clientJarUrl,
            String clientMappingsUrl, String clientMappingsSha1, long mappingsSize) {
    }

    /** The parsed manifest: ids with their per-version JSON URLs. */
    public record Manifest(java.util.Map<String, String> versionUrls, String latest) {
    }

    /** Default Mojang manifest location (override via {@link #fetchManifest(URI)}). */
    public static final String DEFAULT_MANIFEST_URL =
            "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";

    private final HttpClient http;

    public MojangVersionIndex(HttpClient http) {
        this.http = http;
    }

    /**
     * Fetches and parses the version manifest.
     *
     * @throws IOException on network or parse failure
     */
    public Manifest fetchManifest() throws IOException {
        return fetchManifest(URI.create(DEFAULT_MANIFEST_URL));
    }

    /**
     * Fetches and parses the version manifest from an explicit URL (used by
     * tests to point at a local fixture server).
     */
    public Manifest fetchManifest(URI manifestUri) throws IOException {
        JsonObject root = getJson(manifestUri);
        java.util.Map<String, String> urls = new java.util.LinkedHashMap<>();
        for (JsonElement element : root.getAsJsonArray("versions")) {
            JsonObject entry = element.getAsJsonObject();
            urls.put(entry.get("id").getAsString(), entry.get("url").getAsString());
        }
        String latest = root.getAsJsonObject("latest").get("release").getAsString();
        return new Manifest(java.util.Map.copyOf(urls), latest);
    }

    /**
     * Resolves the authoritative download set for one version id via the
     * manifest.
     */
    public VersionDownloads resolveVersion(Manifest manifest, String version)
            throws IOException {
        String versionUrl = manifest.versionUrls().get(version);
        if (versionUrl == null) {
            throw new IOException("version not in manifest: " + version);
        }
        JsonObject json = getJson(URI.create(versionUrl));
        JsonObject downloads = json.getAsJsonObject("downloads");
        JsonObject client = downloads.getAsJsonObject("client");
        JsonObject mappings = downloads.getAsJsonObject("client_mappings");
        if (mappings == null) {
            throw new IOException("version " + version
                    + " publishes no client_mappings (pre-1.14.4?)");
        }
        return new VersionDownloads(version,
                client.get("url").getAsString(),
                mappings.get("url").getAsString(),
                mappings.get("sha1").getAsString(),
                mappings.get("size").getAsLong());
    }

    private JsonObject getJson(URI uri) throws IOException {
        try {
            HttpResponse<String> response = http.send(
                    HttpRequest.newBuilder(uri).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IOException("HTTP " + response.statusCode()
                        + " for " + uri);
            }
            return JsonParser.parseString(response.body()).getAsJsonObject();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted fetching " + uri, e);
        }
    }

    /**
     * Downloads bytes from a URL.
     */
    public byte[] fetchBytes(URI uri) throws IOException {
        try {
            HttpResponse<byte[]> response = http.send(
                    HttpRequest.newBuilder(uri).GET().build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                throw new IOException("HTTP " + response.statusCode()
                        + " for " + uri);
            }
            return response.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted fetching " + uri, e);
        }
    }

    /**
     * @return a deterministic SHA-1 hex of the content (Mojang publishes
     *         SHA-1 expected hashes for downloads)
     */
    public static String sha1Hex(byte[] content) {
        try {
            java.security.MessageDigest digest =
                    java.security.MessageDigest.getInstance("SHA-1");
            return java.util.HexFormat.of().formatHex(digest.digest(content));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-1 unavailable", impossible);
        }
    }

    /**
     * Small lookup helper used by diagnostics.
     */
    public static Optional<String> firstNonBlank(String value) {
        return value == null || value.isBlank() ? Optional.empty()
                : Optional.of(value);
    }
}
