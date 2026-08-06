package com.aprism.manifest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * JUnit 5 + AssertJ tests for {@link VersionRange}.
 *
 * <p>Covers the range grammar documented in Document 2 section 6:
 * comparators ({@code >= > <= <}), tilde, caret, exact, comma-AND, and Maven
 * bracket forms. Also covers prerelease ordering and "any version" wildcard.
 *
 * @author BlockConnect@StarsailsClover
 */
class VersionRangeTest {

    @Nested
    class AnyVersion {
        @Test
        void emptyStringMatchesAnyVersion() {
            VersionRange r = VersionRange.parse("");
            assertThat(r.contains("1.0.0")).isTrue();
            assertThat(r.contains("26.0.0")).isTrue();
        }

        @Test
        void starMatchesAnyVersion() {
            VersionRange r = VersionRange.parse("*");
            assertThat(r.contains("0.0.1")).isTrue();
            assertThat(r.contains("99.99.99")).isTrue();
        }
    }

    @Nested
    class Comparators {
        @Test
        void greaterOrEqual() {
            VersionRange r = VersionRange.parse(">=1.2.0");
            assertThat(r.contains("1.2.0")).isTrue();
            assertThat(r.contains("1.3.0")).isTrue();
            assertThat(r.contains("2.0.0")).isTrue();
            assertThat(r.contains("1.1.9")).isFalse();
        }

        @Test
        void greaterThan() {
            VersionRange r = VersionRange.parse(">1.2.0");
            assertThat(r.contains("1.2.0")).isFalse();
            assertThat(r.contains("1.2.1")).isTrue();
        }

        @Test
        void lessOrEqual() {
            VersionRange r = VersionRange.parse("<=2.0.0");
            assertThat(r.contains("2.0.0")).isTrue();
            assertThat(r.contains("1.9.9")).isTrue();
            assertThat(r.contains("2.0.1")).isFalse();
        }

        @Test
        void lessThan() {
            VersionRange r = VersionRange.parse("<2.0.0");
            assertThat(r.contains("1.9.9")).isTrue();
            assertThat(r.contains("2.0.0")).isFalse();
        }
    }

    @Nested
    class Tilde {
        @Test
        void tildeMinor() {
            // ~1.2.3 = >=1.2.3, <1.3.0
            VersionRange r = VersionRange.parse("~1.2.3");
            assertThat(r.contains("1.2.3")).isTrue();
            assertThat(r.contains("1.2.9")).isTrue();
            assertThat(r.contains("1.3.0")).isFalse();
            assertThat(r.contains("1.1.9")).isFalse();
        }

        @Test
        void tildeShort() {
            // ~1.2 = >=1.2.0, <1.3.0
            // Note: parser requires 3-component SemVer; ~1.2 parses as 1.2.0
            VersionRange r = VersionRange.parse("~1.2.0");
            assertThat(r.contains("1.2.0")).isTrue();
            assertThat(r.contains("1.2.5")).isTrue();
            assertThat(r.contains("1.3.0")).isFalse();
        }
    }

    @Nested
    class Caret {
        @Test
        void caretStable() {
            // ^1.2.3 = >=1.2.3, <2.0.0
            VersionRange r = VersionRange.parse("^1.2.3");
            assertThat(r.contains("1.2.3")).isTrue();
            assertThat(r.contains("1.9.9")).isTrue();
            assertThat(r.contains("2.0.0")).isFalse();
        }

        @Test
        void caretZeroMajor() {
            // ^0.2.3 = >=0.2.3, <0.3.0
            VersionRange r = VersionRange.parse("^0.2.3");
            assertThat(r.contains("0.2.3")).isTrue();
            assertThat(r.contains("0.2.9")).isTrue();
            assertThat(r.contains("0.3.0")).isFalse();
        }

        @Test
        void caretZeroMajorZeroMinor() {
            // ^0.0.3 = >=0.0.3, <0.0.4
            VersionRange r = VersionRange.parse("^0.0.3");
            assertThat(r.contains("0.0.3")).isTrue();
            assertThat(r.contains("0.0.4")).isFalse();
        }
    }

    @Nested
    class ExactMatch {
        @Test
        void exactBare() {
            VersionRange r = VersionRange.parse("1.5.0");
            assertThat(r.contains("1.5.0")).isTrue();
            assertThat(r.contains("1.5.1")).isFalse();
            assertThat(r.contains("1.4.0")).isFalse();
        }

        @Test
        void exactEquals() {
            VersionRange r = VersionRange.parse("=2.0.0");
            assertThat(r.contains("2.0.0")).isTrue();
            assertThat(r.contains("2.0.1")).isFalse();
        }
    }

    @Nested
    class CommaAnd {
        @Test
        void commaAndRange() {
            VersionRange r = VersionRange.parse(">=1.0.0,<2.0.0");
            assertThat(r.contains("1.0.0")).isTrue();
            assertThat(r.contains("1.5.0")).isTrue();
            assertThat(r.contains("2.0.0")).isFalse();
            assertThat(r.contains("0.9.0")).isFalse();
        }

        @Test
        void commaAndThreeClauses() {
            // Multiple comma-AND clauses intersect
            VersionRange r = VersionRange.parse(">=1.0.0,<2.0.0,>=1.5.0");
            assertThat(r.contains("1.4.0")).isFalse();
            assertThat(r.contains("1.5.0")).isTrue();
            assertThat(r.contains("1.9.9")).isTrue();
        }
    }

    @Nested
    class MavenBracket {
        @Test
        void inclusiveExclusive() {
            // [1.0,2.0) = >=1.0.0, <2.0.0
            VersionRange r = VersionRange.parse("[1.0.0,2.0.0)");
            assertThat(r.contains("1.0.0")).isTrue();
            assertThat(r.contains("1.5.0")).isTrue();
            assertThat(r.contains("2.0.0")).isFalse();
        }

        @Test
        void inclusiveBoth() {
            VersionRange r = VersionRange.parse("[1.0.0,2.0.0]");
            assertThat(r.contains("1.0.0")).isTrue();
            assertThat(r.contains("2.0.0")).isTrue();
            assertThat(r.contains("2.0.1")).isFalse();
        }

        @Test
        void exactBracket() {
            VersionRange r = VersionRange.parse("[1.5.0]");
            assertThat(r.contains("1.5.0")).isTrue();
            assertThat(r.contains("1.5.1")).isFalse();
        }

        @Test
        void openUpper() {
            VersionRange r = VersionRange.parse("[1.0.0,)");
            assertThat(r.contains("1.0.0")).isTrue();
            assertThat(r.contains("99.0.0")).isTrue();
            assertThat(r.contains("0.9.0")).isFalse();
        }
    }

    @Nested
    class Prerelease {
        @Test
        void prereleaseLowerThanRelease() {
            VersionRange r = VersionRange.parse(">=1.0.0");
            // 1.0.0-alpha is < 1.0.0
            assertThat(r.contains("1.0.0-alpha")).isFalse();
            assertThat(r.contains("1.0.0")).isTrue();
        }

        @Test
        void prereleaseOrdering() {
            VersionRange r = VersionRange.parse(">=1.0.0-alpha");
            assertThat(r.contains("1.0.0-alpha")).isTrue();
            assertThat(r.contains("1.0.0-beta")).isTrue();
            assertThat(r.contains("1.0.0")).isTrue();
            assertThat(r.contains("0.9.0")).isFalse();
        }
    }

    @Nested
    class Errors {
        @Test
        void whitespaceOnlyIsAnyVersion() {
            // "   " trims to "" which is the any-version wildcard (not an error)
            VersionRange r = VersionRange.parse("   ");
            assertThat(r.contains("1.0.0")).isTrue();
            assertThat(r.contains("99.99.99")).isTrue();
        }

        @Test
        void invalidSemVerRejected() {
            assertThatThrownBy(() -> VersionRange.parse("not-a-version"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("CHKAPRISM-RANGE-001");
        }

        @Test
        void malformedBracketRejected() {
            assertThatThrownBy(() -> VersionRange.parse("[1.0.0,2.0.0"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("CHKAPRISM-RANGE-001");
        }

        @Test
        void emptyMavenBracketRejected() {
            assertThatThrownBy(() -> VersionRange.parse("[]"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("CHKAPRISM-RANGE-001");
        }
    }

    @Nested
    class AprismUseCases {
        @Test
        void aprismAlphaRange() {
            // Aprism loader range used by .aep manifests
            VersionRange r = VersionRange.parse("[26.0.0,27.0.0)");
            assertThat(r.contains("26.0.0")).isTrue();
            assertThat(r.contains("26.5.9")).isTrue();
            assertThat(r.contains("27.0.0")).isFalse();
        }

        @Test
        void aprismAlphaPrerelease() {
            // Aprism dev versions use SemVer with prerelease tags
            VersionRange r = VersionRange.parse(">=26.0.0-Alpha.1");
            assertThat(r.contains("26.0.0-Alpha.1")).isTrue();
            assertThat(r.contains("26.0.0-Alpha.2")).isTrue();
            assertThat(r.contains("26.0.0")).isTrue();
            assertThat(r.contains("26.0.0-Alpha.0")).isFalse();
        }

        @Test
        void toStringRoundTrip() {
            String expr = ">=1.0.0,<2.0.0";
            VersionRange r = VersionRange.parse(expr);
            assertThat(r.toString()).isEqualTo(expr);
        }
    }
}
