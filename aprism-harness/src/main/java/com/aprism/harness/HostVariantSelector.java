package com.aprism.harness;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Host/variant pairing rules for the fat agent jar (v26.9-Alpha.8).
 *
 * <p>Rationale (Despotes Dev report): the v26.8 GA agent shipped
 * {@code org.objectweb.asm} unrelocated, which duplicates the Fabric host's
 * own ASM and aborts Fabric Loader's classpath verification. The
 * {@code -fabric-host} variant relocates ASM and omits Mixin. Pairing the
 * wrong jar with a host therefore fails in a confusing place, so the harness
 * refuses the pairing up front.
 *
 * @author BlockConnect@StarsailsClover
 */
public final class HostVariantSelector {
    //GitHub@NDBlockConnect | BlockConnect@StarsailsClover

    /** The host families the harness distinguishes. */
    public enum Host {
        /** Aprism's own native runtime (vanilla MC, no foreign loader). */
        NATIVE,
        /** A Fabric loader host (provides ASM and owns Mixin). */
        FABRIC,
        /** A Forge/NeoForge host (own Mixin/ASM stack). */
        FORGE_LIKE
    }

    /** The agent jar layouts the build can produce. */
    public enum Variant {
        /** Keeps org.objectweb.asm + org.spongepowered (Mixin's own needs). */
        NATIVE,
        /** Relocates ASM under aprism.libs.asm and omits Mixin. */
        FABRIC_HOST
    }

    /** A pairing decision with the reason it was made. */
    public record Decision(Variant variant, String reason) {
    }

    private HostVariantSelector() {
    }

    /**
     * Chooses the agent variant for a host.
     *
     * @param host the target host family
     * @return the decision (variant plus the rule that produced it)
     */
    public static Decision select(Host host) {
        return switch (host) {
            case NATIVE -> new Decision(Variant.NATIVE,
                    "native runtime: the agent owns ASM and Mixin (Mixin's "
                            + "ASM-version detection reads org.objectweb.asm)");
            case FABRIC -> new Decision(Variant.FABRIC_HOST,
                    "Fabric host: Fabric Loader verifyClasspath rejects the "
                            + "duplicate org.objectweb.asm package, so ASM must "
                            + "be relocated and Mixin left to the host");
            case FORGE_LIKE -> new Decision(Variant.FABRIC_HOST,
                    "Forge/NeoForge host: the host supplies its own ASM/Mixin "
                            + "stack, so the relocated variant avoids the same "
                            + "duplicate-package failure mode");
        };
    }

    /**
     * Derives the variant a jar file name declares.
     *
     * @param jarName the artifact file name
     * @return the declared variant, or null when the name is not a fat agent
     */
    public static Variant declaredVariant(String jarName) {
        if (jarName == null || !jarName.startsWith("Aprism-")
                || !jarName.endsWith(".jar")) {
            return null;
        }
        return jarName.contains("-fabric-host")
                ? Variant.FABRIC_HOST : Variant.NATIVE;
    }

    /**
     * Verifies that an artifact's declared variant matches the host, and that
     * the jar does not carry both layouts' mutually exclusive packages.
     *
     * @param host the target host
     * @param jarName the artifact file name
     * @param entryNames the jar entry names
     * @return the problems found (empty when the pairing is safe)
     */
    public static List<String> verifyPairing(Host host, String jarName,
            List<String> entryNames) {
        List<String> problems = new ArrayList<>();
        Decision decision = select(host);
        Variant declared = declaredVariant(jarName);
        if (declared == null) {
            problems.add("not a fat agent jar name: " + jarName);
            return List.copyOf(problems);
        }
        if (declared != decision.variant()) {
            problems.add("host " + host + " requires the " + decision.variant()
                    + " layout but " + jarName + " declares " + declared
                    + " (" + decision.reason() + ")");
        }
        boolean hasTopLevelAsm = entryNames.stream()
                .anyMatch(n -> n.startsWith("org/objectweb/asm/"));
        boolean hasRelocatedAsm = entryNames.stream()
                .anyMatch(n -> n.startsWith("aprism/libs/asm/"));
        boolean hasMixin = entryNames.stream()
                .anyMatch(n -> n.startsWith("org/spongepowered/"));
        if (declared == Variant.FABRIC_HOST) {
            if (hasTopLevelAsm) {
                problems.add("fabric-host jar still ships org/objectweb/asm/**: "
                        + "this is the duplicate-package condition that aborts "
                        + "Fabric Loader verifyClasspath");
            }
            if (!hasRelocatedAsm) {
                problems.add("fabric-host jar is missing the relocated "
                        + "aprism/libs/asm/** classes");
            }
            if (hasMixin) {
                problems.add("fabric-host jar still ships org/spongepowered/**: "
                        + "the host owns Mixin there");
            }
        } else {
            if (!hasTopLevelAsm) {
                problems.add("native jar is missing org/objectweb/asm/**: "
                        + "Mixin's ASM-version detection depends on it");
            }
            if (!hasMixin) {
                problems.add("native jar is missing org/spongepowered/**: "
                        + "mod mixins compile against that package");
            }
            if (hasRelocatedAsm) {
                problems.add("native jar unexpectedly ships aprism/libs/asm/**");
            }
        }
        return List.copyOf(problems);
    }

    /**
     * @param hostArg a case-insensitive host name
     * @return the parsed host
     */
    public static Host parseHost(String hostArg) {
        String normalized = hostArg == null ? ""
                : hostArg.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        return switch (normalized) {
            case "NATIVE", "VANILLA" -> Host.NATIVE;
            case "FABRIC" -> Host.FABRIC;
            case "FORGE", "NEOFORGE", "FORGE_LIKE" -> Host.FORGE_LIKE;
            default -> throw new IllegalArgumentException(
                    "unknown host: " + hostArg + " (expected native|fabric|forge|neoforge)");
        };
    }
}
