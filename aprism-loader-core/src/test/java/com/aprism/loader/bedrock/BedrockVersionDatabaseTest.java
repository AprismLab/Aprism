package com.aprism.loader.bedrock;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for the fail-closed {@link BedrockVersionDatabase}.
 *
 * @author BlockConnect@StarsailsClover
 */
class BedrockVersionDatabaseTest {

    private BedrockVersionDatabase db;

    @BeforeEach
    void setUp() {
        db = new BedrockVersionDatabase();
    }

    @Test
    void lookupRegisteredVersionReturnsEntry() {
        db.register(new BedrockVersionDatabase.VersionEntry("26.2.0", 42, true, ""));
        assertThat(db.lookup("26.2.0")).isPresent();
        assertThat(db.lookup("26.2.0").get().signatureCount()).isEqualTo(42);
    }

    @Test
    void lookupUnknownVersionIsEmpty() {
        assertThat(db.lookup("99.9.9")).isEmpty();
    }

    @Test
    void lookupNullIsEmpty() {
        assertThat(db.lookup(null)).isEmpty();
    }

    @Test
    void normalizationStripsLeadingVAndWhitespace() {
        db.register(new BedrockVersionDatabase.VersionEntry("26.2.0", 10, true, ""));
        assertThat(db.lookup("v26.2.0")).isPresent();
        assertThat(db.lookup(" 26.2.0 ")).isPresent();
        assertThat(db.lookup("V26.2.0")).isPresent();
    }

    @Test
    void normalizationIsCaseInsensitive() {
        db.register(new BedrockVersionDatabase.VersionEntry("26.2.0", 10, true, ""));
        assertThat(db.lookup("26.2.0")).isPresent();
        assertThat(db.lookup("26.2.0".toUpperCase())).isPresent();
    }

    @Test
    void isSupportedTrueOnlyForKnownSupportedVersions() {
        db.register(new BedrockVersionDatabase.VersionEntry("26.2.0", 42, true, ""));
        db.register(new BedrockVersionDatabase.VersionEntry("26.1.0", 40, false, "incomplete sigs"));
        assertThat(db.isSupported("26.2.0")).isTrue();
        assertThat(db.isSupported("26.1.0")).isFalse();
        assertThat(db.isSupported("1.0.0")).isFalse();
        assertThat(db.isSupported(null)).isFalse();
    }

    @Test
    void sizeReflectsRegisteredEntries() {
        assertThat(db.size()).isZero();
        db.register(new BedrockVersionDatabase.VersionEntry("26.2.0", 42, true, ""));
        db.register(new BedrockVersionDatabase.VersionEntry("26.1.0", 40, true, ""));
        assertThat(db.size()).isEqualTo(2);
    }
}
