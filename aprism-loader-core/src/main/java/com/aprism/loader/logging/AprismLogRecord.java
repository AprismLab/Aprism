package com.aprism.loader.logging;

/**
 * A single structured log record produced by the Aprism logging facility
 * (v26.2-Alpha.1, goal #6). Records are immutable and carry the wall-clock
 * time, severity, emitting unit (mod id, extension id, or runtime component),
 * message, and an optional throwable.
 *
 * @param epochMillis wall-clock time in milliseconds since the epoch
 * @param level       the severity level
 * @param unit        the emitting unit name (never null)
 * @param message     the log message (never null)
 * @param throwable   the associated throwable, or null
 * @author BlockConnect@StarsailsClover
 */
public record AprismLogRecord(
        long epochMillis,
        AprismLogLevel level,
        String unit,
        String message,
        Throwable throwable
) {

    /**
     * Renders the record as a single log line:
     * {@code [ISO-8601] LEVEL unit - message}.
     *
     * @return the rendered line
     */
    public String render() {
        StringBuilder sb = new StringBuilder();
        sb.append('[')
          .append(java.time.Instant.ofEpochMilli(epochMillis))
          .append("] ")
          .append(level.name())
          .append(' ')
          .append(unit)
          .append(" - ")
          .append(message);
        if (throwable != null) {
            sb.append(": ").append(throwable);
        }
        return sb.toString();
    }
}
