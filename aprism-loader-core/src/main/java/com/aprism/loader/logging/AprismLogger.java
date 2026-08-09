package com.aprism.loader.logging;

/**
 * Per-unit logger handed to mods, extensions, and runtime components
 * (v26.2-Alpha.1, goal #6). Instances are cheap, immutable bindings of a
 * unit name to the shared {@link AprismLogging} facility; obtain them via
 * {@link AprismLogging#getLogger(String)}.
 *
 * @author BlockConnect@StarsailsClover
 */
public final class AprismLogger {

    private final AprismLogging facility;
    private final String unit;

    AprismLogger(AprismLogging facility, String unit) {
        this.facility = facility;
        this.unit = unit;
    }

    /**
     * @return the unit name this logger is bound to
     */
    public String getUnit() {
        return unit;
    }

    /**
     * Logs a message at the given level.
     *
     * @param level   the severity level
     * @param message the message
     */
    public void log(AprismLogLevel level, String message) {
        log(level, message, null);
    }

    /**
     * Logs a message with a throwable at the given level.
     *
     * @param level     the severity level
     * @param message   the message
     * @param throwable the associated throwable, or null
     */
    public void log(AprismLogLevel level, String message, Throwable throwable) {
        facility.emit(new AprismLogRecord(
                System.currentTimeMillis(), level, unit,
                message == null ? "" : message, throwable));
    }

    /**
     * Logs at TRACE.
     *
     * @param message the message
     */
    public void trace(String message) {
        log(AprismLogLevel.TRACE, message);
    }

    /**
     * Logs at DEBUG.
     *
     * @param message the message
     */
    public void debug(String message) {
        log(AprismLogLevel.DEBUG, message);
    }

    /**
     * Logs at INFO.
     *
     * @param message the message
     */
    public void info(String message) {
        log(AprismLogLevel.INFO, message);
    }

    /**
     * Logs at WARN.
     *
     * @param message the message
     */
    public void warn(String message) {
        log(AprismLogLevel.WARN, message);
    }

    /**
     * Logs at ERROR.
     *
     * @param message the message
     */
    public void error(String message) {
        log(AprismLogLevel.ERROR, message);
    }

    /**
     * Logs at ERROR with a throwable.
     *
     * @param message   the message
     * @param throwable the associated throwable
     */
    public void error(String message, Throwable throwable) {
        log(AprismLogLevel.ERROR, message, throwable);
    }
}
