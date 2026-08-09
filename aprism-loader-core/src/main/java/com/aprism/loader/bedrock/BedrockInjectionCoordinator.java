package com.aprism.loader.bedrock;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import com.aprism.loader.BedrockModDiscoverer.BedrockPlatform;
import com.aprism.loader.LoadedBedrockModContainer;
import com.aprism.manifest.ManifestParseException;

/**
 * Coordinates Bedrock Edition injection by composing the fail-closed
 * {@link BedrockVersionDatabase} (loaded from disk by
 * {@link BedrockSignatureDbLoader}) with the pure-Java
 * {@link BedrockInjectionPlan}.
 *
 * <p>This is the highest-level pure-Java layer of BE injection (FACT.md 9.8):
 * it decides, before any native work happens, whether injection is permitted
 * and what it would do. The actual process attachment and hook application
 * remain the native platform injector's responsibility.
 *
 * <p><b>Fail-closed.</b> Every condition that could make injection unsafe
 * yields a {@link CoordinationResult} with {@code feasible() == false} and a
 * human-readable refusal reason rather than an exception or a partial plan:
 * missing/corrupt signature database, undetectable platform, unknown or
 * unsupported BE version, no mods, or no native libraries for the platform.
 *
 * @author BlockConnect@StarsailsClover
 */
public final class BedrockInjectionCoordinator {

    /** Default location of the signature database, relative to the game root. */
    public static final Path SIGNATURE_DB_RELATIVE = Path.of("aprism-signatures", "signatures.json");

    private final BedrockSignatureDbLoader loader;

    /** Creates a coordinator with the default signature database loader. */
    public BedrockInjectionCoordinator() {
        this(new BedrockSignatureDbLoader());
    }

    /**
     * Creates a coordinator with an injected loader (for tests).
     *
     * @param loader the signature database loader
     */
    public BedrockInjectionCoordinator(BedrockSignatureDbLoader loader) {
        this.loader = Objects.requireNonNull(loader, "loader must not be null");
    }

    /**
     * The outcome of coordinating BE injection.
     *
     * @param attempted      whether coordination actually ran (always true from
     *                       {@link #coordinate}; reserved for future short-circuits)
     * @param feasible       whether injection may proceed
     * @param plan           the injection plan (null when refused before planning,
     *                       e.g. unavailable signature database)
     * @param refusalReason  human-readable fail-closed refusal (null when feasible)
     */
    public record CoordinationResult(boolean attempted, boolean feasible,
                                     BedrockInjectionPlan.Plan plan, String refusalReason) {
        /** @return true if injection may proceed */
        public boolean isFeasible() {
            return feasible;
        }
    }

    /**
     * Coordinates BE injection for the default signature database location
     * under the given game root.
     *
     * @param gameRoot the BE game root (typically {@code com.mojang/})
     * @param beVersion the running Bedrock version identifier
     * @param mods the discovered BE mods
     * @return the coordination result
     */
    public CoordinationResult coordinateForGameRoot(Path gameRoot, String beVersion,
                                                    List<LoadedBedrockModContainer> mods) {
        Objects.requireNonNull(gameRoot, "gameRoot must not be null");
        return coordinate(gameRoot.resolve(SIGNATURE_DB_RELATIVE),
                BedrockPlatform.detect(), beVersion, mods);
    }

    /**
     * Fully-controlled coordination: plans injection from an explicit signature
     * database path and platform. This is the testable core.
     *
     * @param signatureDbPath the path to the signature database JSON file
     * @param platform        the target platform (may be null if undetectable)
     * @param beVersion       the running Bedrock version identifier
     * @param mods            the discovered BE mods
     * @return the coordination result (never null; always attempted)
     */
    public CoordinationResult coordinate(Path signatureDbPath, BedrockPlatform platform,
                                         String beVersion, List<LoadedBedrockModContainer> mods) {
        Objects.requireNonNull(signatureDbPath, "signatureDbPath must not be null");
        Objects.requireNonNull(mods, "mods must not be null");

        // Fail-closed: the signature database must load cleanly.
        BedrockVersionDatabase db;
        try {
            db = loader.load(signatureDbPath);
        } catch (ManifestParseException e) {
            return new CoordinationResult(true, false, null,
                    "signature database unavailable: " + e.getMessage());
        }

        // Fail-closed: the version must adapt (parse, in 26.x scope, and be in the DB).
        BedrockVersionAdapter adapter = new BedrockVersionAdapter();
        BedrockVersionAdapter.AdapterResult adapted = adapter.adapt(beVersion, db);
        if (!adapted.isResolved()) {
            return new CoordinationResult(true, false, null, adapterRefusalText(adapted, beVersion));
        }

        // Fail-closed: the platform must be detectable.
        if (platform == null) {
            return new CoordinationResult(true, false, null,
                    "unable to detect the current Bedrock platform");
        }

        // Plan against the adapter-normalized version key.
        BedrockInjectionPlan planner = new BedrockInjectionPlan(db);
        BedrockInjectionPlan.Plan plan = planner.plan(adapted.normalizedVersion(), platform, mods);
        if (!plan.isFeasible()) {
            return new CoordinationResult(true, false, plan,
                    refusalText(plan, platform, adapted.normalizedVersion()));
        }
        return new CoordinationResult(true, true, plan, null);
    }

    /**
     * Builds a human-readable refusal message from a refused adapter result.
     */
    private static String adapterRefusalText(BedrockVersionAdapter.AdapterResult adapted, String raw) {
        return switch (adapted.refusal()) {
            case UNPARSEABLE -> "running BE version '" + raw + "' is not parseable";
            case OUT_OF_SCOPE -> "running BE version '" + adapted.normalizedVersion()
                    + "' is outside the supported scope (Bedrock "
                    + BedrockVersionAdapter.MIN_MAJOR_VERSION + ".x and later only)";
            case NOT_IN_DATABASE -> "running BE version '" + adapted.normalizedVersion()
                    + "' is not present in the signature database";
        };
    }

    /**
     * Builds a human-readable refusal message from a refused plan.
     */
    private static String refusalText(BedrockInjectionPlan.Plan plan,
                                      BedrockPlatform platform, String beVersion) {
        return switch (plan.refusal()) {
            case VERSION_UNKNOWN -> "running BE version '" + beVersion
                    + "' is not present in the signature database";
            case VERSION_UNSUPPORTED -> "running BE version '" + beVersion
                    + "' is present but marked unsupported";
            case NO_MODS -> "no Bedrock mods were discovered";
            case NO_NATIVE_LIBS -> "no native libraries exist for platform '" + platform.id() + "'";
        };
    }
}
