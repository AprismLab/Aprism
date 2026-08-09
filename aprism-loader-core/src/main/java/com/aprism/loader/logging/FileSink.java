package com.aprism.loader.logging;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.logging.Logger;

/**
 * File sink for the Aprism structured logging facility
 * (v26.2-Alpha.1, goal #6). Appends rendered records to a log file under
 * the instance directory (typically {@code <game-root>/aprism-logs/}).
 * Write failures are swallowed: logging must never crash the host game.
 *
 * @author BlockConnect@StarsailsClover
 */
public final class FileSink implements AprismLogSink {

    private static final Logger LOG = Logger.getLogger(FileSink.class.getName());

    private final Path file;
    private BufferedWriter writer;

    /**
     * Opens (creating if necessary, appending if present) the given file.
     *
     * @param file the log file path
     * @throws IOException if the file cannot be opened
     */
    public FileSink(Path file) throws IOException {
        this.file = file;
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        this.writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    @Override
    public synchronized void write(AprismLogRecord record) {
        if (writer == null) {
            return;
        }
        try {
            writer.write(record.render());
            writer.newLine();
        } catch (IOException e) {
            LOG.fine("Aprism FileSink write failed: " + e.getMessage());
        }
    }

    @Override
    public synchronized void flush() {
        if (writer == null) {
            return;
        }
        try {
            writer.flush();
        } catch (IOException e) {
            LOG.fine("Aprism FileSink flush failed: " + e.getMessage());
        }
    }

    @Override
    public synchronized void close() {
        if (writer == null) {
            return;
        }
        try {
            writer.close();
        } catch (IOException e) {
            LOG.fine("Aprism FileSink close failed: " + e.getMessage());
        }
        writer = null;
    }

    /**
     * @return the log file this sink writes to
     */
    public Path getFile() {
        return file;
    }
}
