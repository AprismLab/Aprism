package com.aprism.loader.logging;

import java.io.PrintStream;

/**
 * Console sink for the Aprism structured logging facility
 * (v26.2-Alpha.1, goal #6). Renders each record through
 * {@link AprismLogRecord#render()} onto a {@link PrintStream}; by default
 * INFO and below go to stdout, WARN and ERROR go to stderr.
 *
 * @author BlockConnect@StarsailsClover
 */
public final class ConsoleSink implements AprismLogSink {

    private final PrintStream stdout;
    private final PrintStream stderr;

    /**
     * Creates a sink on {@link System#out} and {@link System#err}.
     */
    public ConsoleSink() {
        this(System.out, System.err);
    }

    /**
     * @param stdout the stream for TRACE/DEBUG/INFO records
     * @param stderr the stream for WARN/ERROR records
     */
    public ConsoleSink(PrintStream stdout, PrintStream stderr) {
        this.stdout = stdout;
        this.stderr = stderr;
    }

    @Override
    public void write(AprismLogRecord record) {
        PrintStream target = record.level().ordinal() >= AprismLogLevel.WARN.ordinal()
                ? stderr
                : stdout;
        synchronized (target) {
            target.println(record.render());
        }
    }

    @Override
    public void flush() {
        stdout.flush();
        stderr.flush();
    }
}
