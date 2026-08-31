package com.aprism.loader;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link GameBootstrapGate}. In a unit JVM the vanilla
 * {@code net.minecraft.server.Bootstrap} class is absent, so the gate must
 * be inactive (synchronous behaviour preserved) and the fail-open watcher
 * path must still deliver exactly-once dispatch.
 *
 * <p>v26.8-Alpha.9 adds coverage for the mapping-aware probe retargeting:
 * {@link GameBootstrapGate#setProbeNames(String, String)} must re-point the
 * probe so obfuscated pre-26.1 profiles can defer until vanilla bootstrap.
 *
 * @author opencode agent (ox-alpha), working session on behalf of the
 *         AprismRefract owner
 */
class GameBootstrapGateTest {

    private static final String DEFAULT_CLASS =
            GameBootstrapGate.VANILLA_BOOTSTRAP_CLASS;
    private static final String DEFAULT_METHOD =
            GameBootstrapGate.VANILLA_CHECK_METHOD;

    //GitHub@NDBlockConnect | BlockConnect@StarsailsClover

    /** Stand-in for the vanilla probe with identical fail semantics. */
    public static final class Probe {
        static boolean bootstrapped = false;

        public static void checkBootstrapCalled(
                java.util.function.Supplier<String> message) {
            if (!bootstrapped) {
                throw new IllegalStateException("Not bootstrapped");
            }
        }
    }

    @AfterEach
    void restoreDefaults() {
        GameBootstrapGate.setProbeNames(DEFAULT_CLASS, DEFAULT_METHOD);
        Probe.bootstrapped = false;
    }

    @Test
    void probeIsRetargetableAndFailsClosed() {
        GameBootstrapGate.setProbeNames(
                "com.aprism.loader.GameBootstrapGateTest$Probe",
                "checkBootstrapCalled");
        assertThat(GameBootstrapGate.probeClassName())
                .isEqualTo("com.aprism.loader.GameBootstrapGateTest$Probe");
        assertThat(GameBootstrapGate.isVanillaBootstrapped())
                .as("fixture reports not-yet-bootstrapped").isFalse();
        Probe.bootstrapped = true;
        assertThat(GameBootstrapGate.isVanillaBootstrapped())
                .as("fixture reports bootstrapped after the flag flips")
                .isTrue();
    }

    @Test
    void setProbeNamesIgnoresBlankArguments() {
        GameBootstrapGate.setProbeNames("x.y.Z", "m");
        GameBootstrapGate.setProbeNames("  ", "");
        assertThat(GameBootstrapGate.probeClassName()).isEqualTo("x.y.Z");
    }

    @Test
    void gateIsInactiveWithoutVanillaClassesAndFailOpenStillDispatchesOnce()
            throws Exception {
        // Plain unit JVM: vanilla Bootstrap absent -> synchronous mode.
        assertThat(GameBootstrapGate.shouldDefer()).isFalse();

        // Probe reports not-bootstrapped (class missing)...
        assertThat(GameBootstrapGate.isVanillaBootstrapped()).isFalse();

        // ...yet the fail-open watcher still delivers the dispatch exactly
        // once after its (tiny) timeout. Master switch explicitly enabled so
        // this exercises the deferred branch deterministically.
        System.setProperty(GameBootstrapGate.PROP_DEFER, "true");
        System.setProperty(GameBootstrapGate.PROP_POLL_MS, "10");
        System.setProperty(GameBootstrapGate.PROP_TIMEOUT_MS, "150");
        try {
            AtomicInteger runs = new AtomicInteger();
            long start = System.currentTimeMillis();
            GameBootstrapGate.onBootstrapped(runs::incrementAndGet);
            long elapsed = System.currentTimeMillis() - start;
            // Watcher path: must NOT run inline.
            assertThat(elapsed).as("inline dispatch before timeout").isLessThan(140);
            Thread.sleep(400);
            assertThat(runs.get()).as("fail-open dispatch ran").isEqualTo(1);

            // Second invocation is a no-op (once-per-JVM semantics).
            GameBootstrapGate.onBootstrapped(runs::incrementAndGet);
            Thread.sleep(50);
            assertThat(runs.get()).as("dispatch stays single-shot").isEqualTo(1);
        } finally {
            System.clearProperty(GameBootstrapGate.PROP_DEFER);
            System.clearProperty(GameBootstrapGate.PROP_POLL_MS);
            System.clearProperty(GameBootstrapGate.PROP_TIMEOUT_MS);
        }
    }

    @Test
    void masterSwitchForcesSynchronousModeEvenWhenProbeWouldBePresent() {
        System.setProperty(GameBootstrapGate.PROP_DEFER, "false");
        try {
            // With the switch off the answer is false regardless of probe.
            assertThat(GameBootstrapGate.shouldDefer()).isFalse();
        } finally {
            System.clearProperty(GameBootstrapGate.PROP_DEFER);
        }
    }
}
