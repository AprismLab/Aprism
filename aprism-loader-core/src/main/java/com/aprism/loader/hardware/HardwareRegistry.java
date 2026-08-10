package com.aprism.loader.hardware;

import com.aprism.api.hardware.HardwareInsight;
import com.aprism.api.hardware.HardwareProbe;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Registry of hardware probes (v26.4-Alpha.7, performance &amp; hardware
 * fusion reference). The default probe is always registered and active; a
 * deeper probe (the AprismJDK native probe) may be registered and
 * activated to replace the insight with hardware-backed values. All values
 * are advisory: unknown quantities carry the {@code -1} sentinel.
 *
 * @author BlockConnect@StarsailsClover
 */
public final class HardwareRegistry {

    private static final Logger LOG = Logger.getLogger("aprism.hardware");

    private final Map<String, HardwareProbe> probes = new ConcurrentHashMap<>();
    private volatile HardwareProbe activeProbe;

    /**
     * Creates the registry with the default probe active.
     */
    public HardwareRegistry() {
        DefaultHardwareProbe defaultProbe = new DefaultHardwareProbe();
        probes.put(defaultProbe.name(), defaultProbe);
        activeProbe = defaultProbe;
    }

    /**
     * Registers a probe. Duplicate names are refused; registering an
     * unavailable probe is refused fail-closed.
     *
     * @param probe the probe
     * @return the registered probe
     * @throws IllegalArgumentException on duplicate name or unavailable
     *                                  probe
     */
    public HardwareProbe register(HardwareProbe probe) {
        Objects.requireNonNull(probe, "probe");
        if (probes.putIfAbsent(probe.name(), probe) != null) {
            throw new IllegalArgumentException("probe already registered: " + probe.name());
        }
        return probe;
    }

    /**
     * Activates a registered probe.
     *
     * @param name the probe name
     * @return whether the probe was found, available and activated
     */
    public boolean activate(String name) {
        HardwareProbe probe = probes.get(name);
        if (probe == null) {
            return false;
        }
        if (!probe.isAvailable()) {
            return false;
        }
        activeProbe = probe;
        LOG.info("Hardware probe activated: " + name);
        return true;
    }

    /**
     * @return the name of the active probe
     */
    public String getActiveProbeName() {
        return activeProbe.name();
    }

    /**
     * @return the names of all registered probes
     */
    public List<String> getProbeNames() {
        return List.copyOf(probes.keySet());
    }

    /**
     * @return the hardware insight from the active probe (fail-safe: a
     *         throwing probe falls back to the default probe)
     */
    public HardwareInsight insight() {
        try {
            return activeProbe.probe();
        } catch (RuntimeException e) {
            LOG.warning("Hardware probe " + activeProbe.name()
                    + " failed; falling back to default: " + e.getMessage());
            return new DefaultHardwareProbe().probe();
        }
    }

    /**
     * Resets the registry to the default probe only. Called by the loader
     * on shutdown.
     */
    public void clear() {
        DefaultHardwareProbe defaultProbe = new DefaultHardwareProbe();
        probes.clear();
        probes.put(defaultProbe.name(), defaultProbe);
        activeProbe = defaultProbe;
    }
}
