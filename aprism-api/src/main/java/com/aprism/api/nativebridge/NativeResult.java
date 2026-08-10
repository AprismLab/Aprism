package com.aprism.api.nativebridge;

import java.util.Optional;

/**
 * The result of a native interop operation (v26.4-Alpha.5, native interop
 * bridge). Follows the capability-gated contract used across Aprism:
 * operations never throw into the game — a failure is expressed as an
 * empty result with a reason.
 *
 * @param success whether the operation succeeded
 * @param reason the failure reason (empty on success)
 * @param value the operation value, when one applies
 * @author BlockConnect@StarsailsClover
 */
public record NativeResult(boolean success, String reason, Optional<Object> value) {

    /**
     * @param value the successful value
     * @return a successful result carrying the value
     */
    public static NativeResult ok(Object value) {
        return new NativeResult(true, "", Optional.ofNullable(value));
    }

    /**
     * @param reason why the operation failed
     * @return a failed result with the given reason
     */
    public static NativeResult refused(String reason) {
        return new NativeResult(false, reason, Optional.empty());
    }
}
