package com.aprism.loader.bedrock;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import com.aprism.loader.BedrockModDiscoverer.BedrockPlatform;
import com.aprism.loader.LoadedBedrockModContainer;

/**
 * Computes, for a given Bedrock platform and game version, the set of native
 * injection actions the platform injector must perform.
 *
 * <p>This is the pure-Java, testable half of BE injection: it never touches a
 * process or a binary. It decides, per FACT.md 9.8 fail-closed policy,
 * <em>whether</em> injection may proceed (version supported?) and
 * <em>what</em> must be loaded (which native libraries, for which mods). The
 * actual process attachment and hook application is the platform injector's
 * job (a native component, out of scope for this class).
 *
 * @author BlockConnect@StarsailsClover
 */
public final class BedrockInjectionPlan {

    /**
     * The reason an injection plan was refused, when {@link Plan#feasible()} is
     * false.
     */
    public enum RefusalReason {
        /** The running BE version is not present in the version database. */
        VERSION_UNKNOWN,
        /** The version is present but marked unsupported. */
        VERSION_UNSUPPORTED,
        /** No mods were discovered to inject. */
        NO_MODS,
        /** No native libraries exist for the target platform. */
        NO_NATIVE_LIBS
    }

    /**
     * A single native injection action: load one native library entry for one
     * mod.
     *
     * @param modId     the mod id owning the library
     * @param platform  the target platform
     * @param entryPath the library entry path inside the mod archive
     */
    public record InjectionAction(String modId, BedrockPlatform platform, String entryPath) {
    }

    /**
     * The outcome of planning: feasible or refused.
     *
     * @param feasible   whether injection may proceed
     * @param refusal    the refusal reason (null when feasible)
     * @param actions    the ordered injection actions (empty when refused)
     * @param beVersion  the Bedrock version the plan targets
     */
    public record Plan(boolean feasible, RefusalReason refusal, List<InjectionAction> actions, String beVersion) {
        /** @return true if injection may proceed */
        public boolean isFeasible() {
            return feasible;
        }
    }

    private final BedrockVersionDatabase versionDatabase;

    /**
     * @param versionDatabase the fail-closed version database
     */
    public BedrockInjectionPlan(BedrockVersionDatabase versionDatabase) {
        this.versionDatabase = Objects.requireNonNull(versionDatabase, "versionDatabase must not be null");
    }

    /**
     * Plans native injection for the given game version, platform, and mods.
     *
     * <p>Fail-closed ordering: the version is validated first; only if it is
     * known and supported do we proceed to collect actions. If any refusal
     * condition applies, a refused plan is returned and no actions are emitted.
     *
     * @param beVersion the running Bedrock version identifier
     * @param platform  the target platform
     * @param mods      the discovered BE mods
     * @return the plan (feasible with actions, or refused)
     */
    public Plan plan(String beVersion, BedrockPlatform platform, List<LoadedBedrockModContainer> mods) {
        Objects.requireNonNull(platform, "platform must not be null");
        Objects.requireNonNull(mods, "mods must not be null");

        // Fail-closed version gate.
        var entry = versionDatabase.lookup(beVersion);
        if (entry.isEmpty()) {
            return new Plan(false, RefusalReason.VERSION_UNKNOWN, List.of(), beVersion);
        }
        if (!entry.get().isSupported()) {
            return new Plan(false, RefusalReason.VERSION_UNSUPPORTED, List.of(), beVersion);
        }

        if (mods.isEmpty()) {
            return new Plan(false, RefusalReason.NO_MODS, List.of(), beVersion);
        }

        List<InjectionAction> actions = new ArrayList<>();
        for (LoadedBedrockModContainer mod : mods) {
            for (String entryPath : mod.getNativeLibraries(platform)) {
                actions.add(new InjectionAction(mod.getId(), platform, entryPath));
            }
        }
        if (actions.isEmpty()) {
            return new Plan(false, RefusalReason.NO_NATIVE_LIBS, List.of(), beVersion);
        }

        return new Plan(true, null, Collections.unmodifiableList(actions), beVersion);
    }
}
