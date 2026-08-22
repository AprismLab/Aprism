package com.aprism.loader.distribution;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Resolves download locations for Aprism artifacts across the distribution
 * channels (v26.6-Alpha.3, closes known-issue #13).
 *
 * <p>Aprism is distributed through two channels:
 * <ul>
 *   <li><b>GitHub Releases</b> (primary): versioned tags carry the fat agent
 *       jar, checksums.txt, cosign .sig/.bundle and the CycloneDX SBOM.</li>
 *   <li><b>Modrinth</b> (mirror): the same agent jar mirrored for
 *       discoverability; Modrinth assigns its own opaque version ids, so this
 *       channel resolves to the project page plus the deterministic file name.</li>
 * </ul>
 *
 * <p>The resolver is pure (no network IO); it computes canonical,
 * machine-readable locations that installers, MDL and documentation can
 * link against.
 *
 * @author BlockConnect@StarsailsClover
 */
public final class DistributionResolver {

    /** The GitHub organization/repo hosting primary releases. */
    public static final String GITHUB_REPO = "AprismLab/Aprism";

    /** The Modrinth project slug for the mirror. */
    public static final String MODRINTH_SLUG = "aprism";

    private DistributionResolver() {
    }

    /**
     * Builds the canonical artifact file name for a release.
     *
     * @param version   the Aprism version string (e.g. {@code v26.6} or
     *                  {@code v26.6-Alpha.1})
     * @param mcVersion the target Minecraft version (e.g. {@code 26.2})
     * @return the artifact file name, e.g. {@code Aprism-v26.6-JE-26.2.jar}
     */
    public static String artifactName(String version, String mcVersion) {
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(mcVersion, "mcVersion");
        return "Aprism-" + version + "-JE-" + mcVersion + ".jar";
    }

    /**
     * Describes one downloadable artifact location.
     *
     * @param channel the distribution channel
     * @param url     the canonical download/page URL
     * @param primary whether this channel is the primary distribution point
     */
    public record ArtifactLocation(DistributionChannel channel, String url, boolean primary) {
    }

    /**
     * Resolves every channel location for a release artifact.
     *
     * @param version   the Aprism version string
     * @param mcVersion the target Minecraft version
     * @return the list of locations, primary channel first
     */
    public static List<ArtifactLocation> resolveArtifact(String version, String mcVersion) {
        String name = artifactName(version, mcVersion);
        List<ArtifactLocation> locations = new ArrayList<>();
        locations.add(new ArtifactLocation(DistributionChannel.GITHUB_RELEASES,
                "https://github.com/" + GITHUB_REPO + "/releases/download/"
                        + version + "/" + name,
                true));
        locations.add(new ArtifactLocation(DistributionChannel.MODRINTH,
                "https://modrinth.com/mod/" + MODRINTH_SLUG,
                false));
        return locations;
    }

    /**
     * Resolves the checksums.txt location on the primary channel. The mirror
     * does not carry standalone checksum files; verification on the mirror
     * relies on Modrinth's own SHA1/SHA512 sidecar hashes.
     *
     * @param version the Aprism version string (the release tag)
     * @return the checksums.txt URL, or null when the version is null
     */
    public static String resolveChecksums(String version) {
        if (version == null) {
            return null;
        }
        return "https://github.com/" + GITHUB_REPO + "/releases/download/"
                + version + "/checksums.txt";
    }

    /**
     * Renders a machine-readable summary of all channels for a release
     * (stable key order; suitable for status documents and tooling).
     *
     * @param version   the Aprism version string
     * @param mcVersion the target Minecraft version
     * @return the summary map with keys {@code artifact}, {@code checksums},
     *         {@code channels} (list of {channel,url,primary})
     */
    public static Map<String, Object> describe(String version, String mcVersion) {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("artifact", artifactName(version, mcVersion));
        doc.put("checksums", resolveChecksums(version));
        List<Map<String, Object>> channels = new ArrayList<>();
        for (ArtifactLocation loc : resolveArtifact(version, mcVersion)) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("channel", loc.channel().id());
            entry.put("url", loc.url());
            entry.put("primary", loc.primary());
            channels.add(entry);
        }
        doc.put("channels", channels);
        return doc;
    }
}
