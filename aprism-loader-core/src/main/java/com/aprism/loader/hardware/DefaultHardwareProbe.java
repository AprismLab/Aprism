package com.aprism.loader.hardware;

import com.aprism.api.hardware.CpuFeatures;
import com.aprism.api.hardware.HardwareInsight;
import com.aprism.api.hardware.HardwareProbe;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * The default hardware probe (v26.4-Alpha.7, performance &amp; hardware
 * fusion reference). Reports only what a stock JVM can PROVE:
 *
 * <ul>
 *   <li>architecture, OS name and processor count from the standard
 *       {@code os.*} system properties and {@link Runtime};</li>
 *   <li>architecturally-guaranteed feature tokens: {@code sse2} on
 *       {@code amd64}/{@code x86_64} (the x86-64 ISA mandates SSE2) and
 *       {@code neon} on {@code aarch64} (ARMv8-A mandates NEON). These
 *       are ISA guarantees, not CPUID probes;</li>
 *   <li>{@code -1} for cache line size and NUMA node count — a stock JVM
 *       cannot prove them; the AprismJDK native probe may fill them in.</li>
 * </ul>
 *
 * @author BlockConnect@StarsailsClover
 */
public final class DefaultHardwareProbe implements HardwareProbe {

    @Override
    public String name() {
        return "default";
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public HardwareInsight probe() {
        String architecture = System.getProperty("os.arch", "unknown");
        String osName = System.getProperty("os.name", "unknown");
        int processors = Runtime.getRuntime().availableProcessors();
        return new HardwareInsight(
                new CpuFeatures(architecture, osName, processors, provenFeatures(architecture)),
                -1, -1);
    }

    /**
     * @param architecture the {@code os.arch} value
     * @return the architecturally-guaranteed feature tokens for the ISA
     */
    private static Set<String> provenFeatures(String architecture) {
        Set<String> features = new LinkedHashSet<>();
        String arch = architecture.toLowerCase();
        if (arch.contains("amd64") || arch.contains("x86_64")) {
            // The x86-64 ISA mandates SSE2.
            features.add("sse2");
        }
        if (arch.contains("aarch64") || arch.contains("arm64")) {
            // ARMv8-A mandates NEON.
            features.add("neon");
        }
        return features;
    }
}
