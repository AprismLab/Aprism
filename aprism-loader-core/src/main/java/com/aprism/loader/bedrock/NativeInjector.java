package com.aprism.loader.bedrock;

import java.nio.file.Path;
import java.util.List;

import com.aprism.loader.BedrockModDiscoverer.BedrockPlatform;

/**
 * Service-provider interface for native Bedrock injection (FACT.md 9.8).
 *
 * <p>The Java side of Aprism owns discovery, the fail-closed version database,
 * and the injection {@link BedrockInjectionPlan}. It never attaches to a
 * process or a binary directly. That work is performed by a platform-specific
 * native injector (Windows proxy-DLL hijack + MinHook/SafetyHook, Android
 * Zygisk/container + ShadowHook, iOS insert_dylib + Dobby, BDS LeviLamina)
 * which implements this SPI and consumes a feasible plan.
 *
 * <p>Lifecycle, in order:
 * <ol>
 *   <li>{@link #attach(AttachmentTarget)}: attach to the target game process /
 *       binary. Fail-closed: return a non-success result if attachment cannot
 *       be done safely for the given platform or version.</li>
 *   <li>{@link #inject(NativeInjectionRequest)} for each staged library: load
 *       the staged native library into the target and run its registration.</li>
 *   <li>{@link #unattach()}: release the attachment (best-effort; a crashed
 *       target may already have been detached).</li>
 * </ol>
 *
 * <p>All methods must be safe to call with a {@code null} plan entry: the
 * caller only feeds actions from a feasible plan, but implementations must not
 * throw on unexpected input; they should return a non-success result instead.
 *
 * @author BlockConnect@StarsailsClover
 */
public interface NativeInjector {

    /** Identifies the native process or binary the injector attaches to. */
    record AttachmentTarget(
            BedrockPlatform platform,
            String beVersion,
            long processId,
            Path gameExecutable) {
    }

    /** One library to inject, already staged on disk (see BedrockNativeStager). */
    record NativeInjectionRequest(String modId, Path stagedLibrary) {
    }

    /** The outcome of an attach / inject / unattach step. */
    record NativeInjectionResult(boolean success, String message) {
        /** @return a successful result with no message */
        public static NativeInjectionResult ok() {
            return new NativeInjectionResult(true, null);
        }

        /**
         * @param message the success detail
         * @return a successful result with a detail message
         */
        public static NativeInjectionResult ok(String message) {
            return new NativeInjectionResult(true, message);
        }

        /**
         * @param message the failure reason
         * @return a failed result with the reason
         */
        public static NativeInjectionResult fail(String message) {
            return new NativeInjectionResult(false, message);
        }

        /** @return true if the step succeeded */
        public boolean isSuccess() {
            return success;
        }
    }

    /**
     * Attaches to the target game process or binary.
     *
     * @param target the attachment target
     * @return the attach outcome
     */
    NativeInjectionResult attach(AttachmentTarget target);

    /**
     * Injects one staged native library into the attached target.
     *
     * @param request the staged library to inject
     * @return the inject outcome
     */
    NativeInjectionResult inject(NativeInjectionRequest request);

    /**
     * Releases the attachment (best-effort).
     *
     * @return the unattach outcome
     */
    NativeInjectionResult unattach();

    /**
     * Convenience: attaches, injects every library from a feasible plan in
     * order, then unattaches. Implementations should stop on the first failed
     * inject and unattach before returning.
     *
     * @param plan     the feasible injection plan
     * @param target   the attachment target
     * @param stagedLibraries map of entry path to staged on-disk library path
     * @return the overall outcome
     */
    default NativeInjectionResult injectAll(BedrockInjectionPlan.Plan plan,
                                            AttachmentTarget target,
                                            java.util.Map<String, Path> stagedLibraries) {
        if (plan == null || !plan.isFeasible()) {
            return NativeInjectionResult.fail("plan is not feasible");
        }
        NativeInjectionResult attachResult = attach(target);
        if (!attachResult.isSuccess()) {
            return attachResult;
        }
        NativeInjectionResult last = NativeInjectionResult.ok();
        for (BedrockInjectionPlan.InjectionAction action : plan.actions()) {
            Path staged = stagedLibraries.get(action.entryPath());
            if (staged == null) {
                last = NativeInjectionResult.fail(
                        "no staged library for entry path: " + action.entryPath());
                break;
            }
            last = inject(new NativeInjectionRequest(action.modId(), staged));
            if (!last.isSuccess()) {
                break;
            }
        }
        unattach();
        return last;
    }
}
