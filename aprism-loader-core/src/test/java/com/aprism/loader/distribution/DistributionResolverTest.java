package com.aprism.loader.distribution;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Tests for {@link DistributionResolver}.
 */
class DistributionResolverTest {

    @Test
    void artifactNameFollowsDocumentedScheme() {
        assertEquals("Aprism-v26.6-JE-26.2.jar",
                DistributionResolver.artifactName("v26.6", "26.2"));
        assertEquals("Aprism-v26.6-Alpha.1-JE-26.2.jar",
                DistributionResolver.artifactName("v26.6-Alpha.1", "26.2"));
    }

    @Test
    void artifactNameRejectsNulls() {
        assertThrows(NullPointerException.class,
                () -> DistributionResolver.artifactName(null, "26.2"));
        assertThrows(NullPointerException.class,
                () -> DistributionResolver.artifactName("v26.6", null));
    }

    @Test
    void resolveArtifactListsPrimaryFirst() {
        List<DistributionResolver.ArtifactLocation> locations =
                DistributionResolver.resolveArtifact("v26.6", "26.2");

        assertEquals(2, locations.size());
        assertTrue(locations.get(0).primary());
        assertFalse(locations.get(1).primary());
        assertEquals(DistributionChannel.GITHUB_RELEASES, locations.get(0).channel());
        assertEquals(DistributionChannel.MODRINTH, locations.get(1).channel());
    }

    @Test
    void githubUrlIsDeterministicTagDownload() {
        List<DistributionResolver.ArtifactLocation> locations =
                DistributionResolver.resolveArtifact("v26.6", "26.2");

        assertEquals("https://github.com/AprismLab/Aprism/releases/download/"
                + "v26.6/Aprism-v26.6-JE-26.2.jar", locations.get(0).url());
    }

    @Test
    void modrinthUrlPointsAtProjectPage() {
        List<DistributionResolver.ArtifactLocation> locations =
                DistributionResolver.resolveArtifact("v26.6", "26.2");

        assertEquals("https://modrinth.com/mod/aprism", locations.get(1).url());
    }

    @Test
    void checksumsResolveOnPrimaryOnly() {
        assertEquals("https://github.com/AprismLab/Aprism/releases/download/"
                        + "v26.6/checksums.txt",
                DistributionResolver.resolveChecksums("v26.6"));
        assertNull(DistributionResolver.resolveChecksums(null));
    }

    @Test
    void describeProducesStableMachineReadableDoc() {
        Map<String, Object> doc = DistributionResolver.describe("v26.6", "26.2");

        assertEquals("Aprism-v26.6-JE-26.2.jar", doc.get("artifact"));
        assertNotNull(doc.get("checksums"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> channels = (List<Map<String, Object>>) doc.get("channels");
        assertEquals(2, channels.size());
        assertEquals("github-releases", channels.get(0).get("channel"));
        assertEquals(Boolean.TRUE, channels.get(0).get("primary"));
        assertEquals("modrinth", channels.get(1).get("channel"));
        assertEquals(Boolean.FALSE, channels.get(1).get("primary"));
    }
}
