package com.aprism.loader.distribution;

/**
 * Distribution channels for Aprism artifacts (v26.6-Alpha.3).
 *
 * @author BlockConnect@StarsailsClover
 */
public enum DistributionChannel {

    /**
     * GitHub Releases: the primary channel carrying the agent jar,
     * checksums.txt, cosign signatures and the SBOM.
     */
    GITHUB_RELEASES("github-releases"),

    /**
     * Modrinth: the discovery mirror carrying the same signed artifact;
     * verification there relies on Modrinth's own sidecar hashes plus the
     * embedded cosign bundle from the primary channel.
     */
    MODRINTH("modrinth");

    private final String id;

    DistributionChannel(String id) {
        this.id = id;
    }

    /**
     * @return the stable machine-readable channel id
     */
    public String id() {
        return id;
    }
}
