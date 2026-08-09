package com.aprism.loader.bedrock;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for the fail-closed {@link BedrockVersionAdapter}, which normalizes a
 * raw BE version, enforces the 26.x-and-later scope, and resolves against the
 * signature database.
 *
 * @author BlockConnect@StarsailsClover
 */
class BedrockVersionAdapterTest {

    private BedrockVersionDatabase db;
    private final BedrockVersionAdapter adapter = new BedrockVersionAdapter();

    @BeforeEach
    void setUp() {
        db = new BedrockVersionDatabase();
        db.register(new BedrockVersionDatabase.VersionEntry("26.2.0", 42, true, ""));
        db.register(new BedrockVersionDatabase.VersionEntry("26.1.0", 40, false, "incomplete"));
    }

    @Test
    void resolvesKnownInScopeVersion() {
        var result = adapter.adapt("26.2.0", db);
        assertThat(result.isResolved()).isTrue();
        assertThat(result.normalizedVersion()).isEqualTo("26.2.0");
        assertThat(result.entry()).isNotNull();
        assertThat(result.entry().signatureCount()).isEqualTo(42);
    }

    @Test
    void normalizesLeadingVAndCase() {
        var result = adapter.adapt("V26.2.0", db);
        assertThat(result.isResolved()).isTrue();
        assertThat(result.normalizedVersion()).isEqualTo("26.2.0");
    }

    @Test
    void refusesUnparseableVersion() {
        assertThat(adapter.adapt(null, db).refusal())
                .isEqualTo(BedrockVersionAdapter.RefusalReason.UNPARSEABLE);
        assertThat(adapter.adapt("  ", db).refusal())
                .isEqualTo(BedrockVersionAdapter.RefusalReason.UNPARSEABLE);
    }

    @Test
    void refusesOutOfScopePre26Version() {
        // 1.21.x is below the 26.x scope boundary, even though no DB entry exists.
        var result = adapter.adapt("1.21.0", db);
        assertThat(result.isResolved()).isFalse();
        assertThat(result.refusal()).isEqualTo(BedrockVersionAdapter.RefusalReason.OUT_OF_SCOPE);
        assertThat(result.normalizedVersion()).isEqualTo("1.21.0");
    }

    @Test
    void refusesOutOfScopeEvenWithDbEntry() {
        // Even if a pre-26 entry existed, the scope boundary refuses it.
        db.register(new BedrockVersionDatabase.VersionEntry("25.0.0", 10, true, ""));
        var result = adapter.adapt("25.0.0", db);
        assertThat(result.isResolved()).isFalse();
        assertThat(result.refusal()).isEqualTo(BedrockVersionAdapter.RefusalReason.OUT_OF_SCOPE);
    }

    @Test
    void refusesInScopeVersionMissingFromDb() {
        var result = adapter.adapt("26.9.9", db);
        assertThat(result.isResolved()).isFalse();
        assertThat(result.refusal()).isEqualTo(BedrockVersionAdapter.RefusalReason.NOT_IN_DATABASE);
    }

    @Test
    void scopeCheckIgnoresUnparseableMajor() {
        // A non-numeric major parses to -1, which fails the scope check.
        var result = adapter.adapt("beta.1.0", db);
        assertThat(result.isResolved()).isFalse();
        assertThat(result.refusal()).isEqualTo(BedrockVersionAdapter.RefusalReason.OUT_OF_SCOPE);
    }

    @Test
    void suffixVersionsMustMatchDbExactly() {
        // A -preview suffix is preserved; no matching entry -> NOT_IN_DATABASE.
        var result = adapter.adapt("26.2.0-preview", db);
        assertThat(result.isResolved()).isFalse();
        assertThat(result.refusal()).isEqualTo(BedrockVersionAdapter.RefusalReason.NOT_IN_DATABASE);
    }

    @Test
    void majorOfExtractsLeadingSegment() {
        assertThat(BedrockVersionAdapter.majorOf("26.2.0")).isEqualTo(26);
        assertThat(BedrockVersionAdapter.majorOf("26.2.0-preview")).isEqualTo(26);
        assertThat(BedrockVersionAdapter.majorOf("26")).isEqualTo(26);
        assertThat(BedrockVersionAdapter.majorOf("abc")).isEqualTo(-1);
    }
}
