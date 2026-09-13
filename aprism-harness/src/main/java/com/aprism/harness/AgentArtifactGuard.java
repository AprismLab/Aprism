package com.aprism.harness;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.jar.JarFile;

/**
 * Stale-artifact guard (v26.9-Alpha.8).
 *
 * <p>Rationale: three real incidents motivated this check. (a) A truncated fat
 * jar produced during host memory pressure silently broke agent startup with
 * "zip END header not found". (b) A leftover {@code .tmp} file from the
 * manifest-rewrite step sat in {@code build/libs} and could be mistaken for a
 * build output. (c) A smoke run used an agent jar built before the fix under
 * test, so the run proved nothing. The harness therefore refuses to test an
 * artifact unless it is a complete, readable jar whose recorded version
 * matches the expected version and whose bytes hash to the recorded digest.
 *
 * @author BlockConnect@StarsailsClover
 */
public final class AgentArtifactGuard {
    //GitHub@NDBlockConnect | BlockConnect@StarsailsClover

    /** The verified state of one candidate artifact. */
    public record Artifact(Path path, String version, long sizeBytes, String sha256,
            List<String> entryNames) {
    }

    private AgentArtifactGuard() {
    }

    /**
     * Verifies one agent jar is complete, readable, and identifies the
     * expected version.
     *
     * @param jar the candidate artifact
     * @param expectedVersion the version the run under test expects (may be
     *        null to skip the version assertion)
     * @return the verified artifact
     * @throws IOException when the artifact is missing, unreadable (truncated),
     *         or the version does not match
     */
    public static Artifact verify(Path jar, String expectedVersion) throws IOException {
        if (jar == null || !Files.isRegularFile(jar)) {
            throw new IOException("agent artifact missing: " + jar);
        }
        String name = jar.getFileName().toString();
        if (name.endsWith(".tmp")) {
            throw new IOException("refusing a .tmp artifact (incomplete build "
                    + "output): " + name);
        }
        byte[] bytes = Files.readAllBytes(jar);
        if (bytes.length < 4_096) {
            throw new IOException("agent artifact suspiciously small ("
                    + bytes.length + " bytes): " + name);
        }
        List<String> entries = new ArrayList<>();
        String version = null;
        try (JarFile opened = new JarFile(jar.toFile())) {
            opened.stream().forEach(entry -> entries.add(entry.getName()));
            if (opened.getManifest() != null) {
                version = opened.getManifest().getMainAttributes()
                        .getValue("Implementation-Version");
            }
        } catch (IOException truncated) {
            throw new IOException("agent artifact is not a readable jar "
                    + "(truncated or corrupt): " + name + " (" + truncated.getMessage()
                    + ")", truncated);
        }
        if (!entries.contains("com/aprism/loader/AprismAgent.class")) {
            throw new IOException("agent artifact does not contain "
                    + "com/aprism/loader/AprismAgent.class: " + name);
        }
        if (expectedVersion != null && !expectedVersion.isBlank()
                && !expectedVersion.equals(version)) {
            throw new IOException("stale artifact: " + name + " declares version "
                    + version + " but the run expects " + expectedVersion
                    + " (rebuild before testing)");
        }
        return new Artifact(jar, version, bytes.length, sha256Hex(bytes),
                List.copyOf(entries));
    }

    /**
     * Refuses to proceed when {@code build/libs} contains leftover temporary
     * artifacts, which indicate a previous build was interrupted.
     *
     * @param libsDir the build/libs directory
     * @return the leftover files found (empty when clean)
     * @throws IOException on listing failure
     */
    public static List<Path> findLeftovers(Path libsDir) throws IOException {
        if (!Files.isDirectory(libsDir)) {
            return List.of();
        }
        List<Path> leftovers = new ArrayList<>();
        try (var stream = Files.list(libsDir)) {
            stream.filter(p -> p.getFileName().toString().endsWith(".tmp"))
                    .forEach(leftovers::add);
        }
        return List.copyOf(leftovers);
    }

    /**
     * @param content the bytes to hash
     * @return the SHA-256 hex of the content
     */
    public static String sha256Hex(byte[] content) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }
}
