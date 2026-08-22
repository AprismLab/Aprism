package com.aprism.loader;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

/**
 * Tests for {@link GameBootstrapGate}. In a unit JVM the vanilla
 * {@code net.minecraft.server.Bootstrap} class is absent, so the gate must
 * be inactive (synchronous behaviour preserved) and the fail-open watcher
 * path must still deliver exactly-once dispatch.
 *
 * @author opencode agent (ox-alpha), working session on behalf of the
 *         AprismRefract owner
 */
class GameBootstrapGateTest {

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
