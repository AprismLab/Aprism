package com.aprism.loader;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link LoadReport} (Alpha 4 production hardening). Covers
 * recording, counting, failure extraction, phase timing, and the plain-text
 * summary rendering.
 *
 * @author BlockConnect@StarsailsClover
 */
class LoadReportTest {

    @Test
    void recordsOkEntries() {
        LoadReport report = new LoadReport();
        report.recordOk("mod", "examplemod", "1.0.0", 12);
        assertThat(report.okCount()).isEqualTo(1);
        assertThat(report.failureCount()).isEqualTo(0);
        assertThat(report.entries()).hasSize(1);
        assertThat(report.entries().get(0).status()).isEqualTo(LoadReport.Entry.Status.OK);
        assertThat(report.entries().get(0).id()).isEqualTo("examplemod");
        assertThat(report.entries().get(0).durationMs()).isEqualTo(12);
    }

    @Test
    void recordsFailureEntries() {
        LoadReport report = new LoadReport();
        report.recordFailure("mod", "brokenmod", "0.1.0", 3, "ClassNotFound");
        assertThat(report.failureCount()).isEqualTo(1);
        assertThat(report.okCount()).isEqualTo(0);
        assertThat(report.failures()).hasSize(1);
        assertThat(report.failures().get(0).failure()).isEqualTo("ClassNotFound");
    }

    @Test
    void separatesOkAndFailed() {
        LoadReport report = new LoadReport();
        report.recordOk("mod", "good", "1.0", 1);
        report.recordFailure("mod", "bad", "1.0", 2, "boom");
        report.recordOk("extension", "ext", "2.0", 3);
        assertThat(report.okCount()).isEqualTo(2);
        assertThat(report.failureCount()).isEqualTo(1);
        assertThat(report.failures()).extracting(LoadReport.Entry::id).containsExactly("bad");
    }

    @Test
    void phaseTimingMarksBoundaries() {
        LoadReport report = new LoadReport();
        report.beginPhase1();
        report.endPhase1();
        report.beginPhase2();
        report.endPhase2();
        // totalMs is monotonic non-negative; phase bounds do not throw
        assertThat(report.totalMs()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void summaryContainsHeaderAndEntries() {
        LoadReport report = new LoadReport();
        report.recordOk("mod", "examplemod", "1.0.0", 5);
        report.recordFailure("extension", "broken-ext", null, 7, "failed to init");
        String summary = report.toSummary("v26.0-Alpha.4");
        assertThat(summary).contains("Aprism Load Report");
        assertThat(summary).contains("v26.0-Alpha.4");
        assertThat(summary).contains("examplemod");
        assertThat(summary).contains("[OK  ]");
        assertThat(summary).contains("[FAIL]");
        assertThat(summary).contains("broken-ext");
        assertThat(summary).contains("failed to init");
        assertThat(summary).contains("Loaded 1, failed 1");
    }

    @Test
    void emptyReportRendersZeroCounts() {
        LoadReport report = new LoadReport();
        String summary = report.toSummary("v26.0-Alpha.4");
        assertThat(summary).contains("Loaded 0, failed 0");
    }

    @Test
    void entriesAreUnmodifiable() {
        LoadReport report = new LoadReport();
        report.recordOk("mod", "a", "1.0", 1);
        assertThat(report.entries()).isUnmodifiable();
    }
}
