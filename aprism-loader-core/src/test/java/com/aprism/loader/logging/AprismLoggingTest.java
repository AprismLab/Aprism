package com.aprism.loader.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for the Aprism structured logging facility (v26.2-Alpha.1, goal #6):
 * level semantics, threshold filtering, ring-buffer retention, console/file
 * sinks, and fail-safe sink isolation.
 *
 * @author BlockConnect@StarsailsClover
 */
class AprismLoggingTest {

    @Nested
    class LevelSemantics {
        @Test
        void levelsOrderedBySeverity() {
            assertThat(AprismLogLevel.TRACE.isEnabledAt(AprismLogLevel.TRACE)).isTrue();
            assertThat(AprismLogLevel.DEBUG.isEnabledAt(AprismLogLevel.TRACE)).isTrue();
            assertThat(AprismLogLevel.INFO.isEnabledAt(AprismLogLevel.DEBUG)).isTrue();
            assertThat(AprismLogLevel.WARN.isEnabledAt(AprismLogLevel.INFO)).isTrue();
            assertThat(AprismLogLevel.ERROR.isEnabledAt(AprismLogLevel.WARN)).isTrue();
        }

        @Test
        void belowThresholdFiltered() {
            assertThat(AprismLogLevel.TRACE.isEnabledAt(AprismLogLevel.INFO)).isFalse();
            assertThat(AprismLogLevel.DEBUG.isEnabledAt(AprismLogLevel.WARN)).isFalse();
            assertThat(AprismLogLevel.INFO.isEnabledAt(AprismLogLevel.ERROR)).isFalse();
        }

        @Test
        void thresholdGatesEmittedRecords() {
            AprismLogging logging = new AprismLogging();
            logging.setThreshold(AprismLogLevel.WARN);
            AprismLogger logger = logging.getLogger("unit");
            logger.trace("t");
            logger.debug("d");
            logger.info("i");
            logger.warn("w");
            logger.error("e");

            List<AprismLogRecord> retained = logging.getRetained().snapshot();
            assertThat(retained).hasSize(2);
            assertThat(retained).extracting(AprismLogRecord::level)
                    .containsExactly(AprismLogLevel.WARN, AprismLogLevel.ERROR);
        }
    }

    @Nested
    class RingBufferRetention {
        @Test
        void retainsInChronologicalOrder() {
            AprismLogging logging = new AprismLogging(100);
            AprismLogger logger = logging.getLogger("unit");
            logger.info("first");
            logger.info("second");
            logger.info("third");

            List<AprismLogRecord> retained = logging.getRetained().snapshot();
            assertThat(retained).extracting(AprismLogRecord::message)
                    .containsExactly("first", "second", "third");
        }

        @Test
        void evictsOldestWhenFull() {
            AprismLogging logging = new AprismLogging(3);
            AprismLogger logger = logging.getLogger("unit");
            for (int i = 0; i < 5; i++) {
                logger.info("msg-" + i);
            }

            List<AprismLogRecord> retained = logging.getRetained().snapshot();
            assertThat(retained).extracting(AprismLogRecord::message)
                    .containsExactly("msg-2", "msg-3", "msg-4");
        }

        @Test
        void invalidCapacityRejected() {
            assertThatThrownBy(() -> new AprismLogRingBuffer(0))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class Sinks {
        @Test
        void consoleSinkRoutesLevelsToStreams() {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ByteArrayOutputStream err = new ByteArrayOutputStream();
            ConsoleSink sink = new ConsoleSink(new PrintStream(out, true), new PrintStream(err, true));

            sink.write(new AprismLogRecord(0, AprismLogLevel.INFO, "unit", "ok", null));
            sink.write(new AprismLogRecord(0, AprismLogLevel.ERROR, "unit", "bad", null));

            assertThat(out.toString(StandardCharsets.UTF_8)).contains("INFO unit - ok");
            assertThat(err.toString(StandardCharsets.UTF_8)).contains("ERROR unit - bad");
            assertThat(out.toString(StandardCharsets.UTF_8)).doesNotContain("ERROR");
        }

        @Test
        void fileSinkAppendsRecords(@TempDir Path dir) throws IOException {
            Path logFile = dir.resolve("aprism-logs").resolve("aprism.log");
            try (FileSink sink = new FileSink(logFile)) {
                sink.write(new AprismLogRecord(0, AprismLogLevel.INFO, "unit", "line-one", null));
                sink.write(new AprismLogRecord(0, AprismLogLevel.WARN, "unit", "line-two", null));
                sink.flush();
            }

            List<String> lines = Files.readAllLines(logFile, StandardCharsets.UTF_8);
            assertThat(lines).hasSize(2);
            assertThat(lines.get(0)).contains("INFO unit - line-one");
            assertThat(lines.get(1)).contains("WARN unit - line-two");
        }

        @Test
        void throwingSinkIsIsolated() {
            AprismLogging logging = new AprismLogging();
            AtomicInteger goodSinkCount = new AtomicInteger();
            logging.attachSink(record -> {
                throw new RuntimeException("synthetic sink failure");
            });
            logging.attachSink(record -> goodSinkCount.incrementAndGet());

            logging.getLogger("unit").info("hello");

            assertThat(goodSinkCount.get()).isEqualTo(1);
            assertThat(logging.getRetained().size()).isEqualTo(1);
        }

        @Test
        void duplicateSinkAttachIgnored() {
            AprismLogging logging = new AprismLogging();
            AprismLogSink sink = record -> { };
            logging.attachSink(sink);
            logging.attachSink(sink);

            assertThat(logging.getSinks()).hasSize(2); // ring buffer + sink
        }
    }

    @Nested
    class LoggerApi {
        @Test
        void loggerCarriesUnitAndLevel() {
            AprismLogging logging = new AprismLogging();
            AprismLogger logger = logging.getLogger("examplemod");
            logger.error("boom", new IllegalStateException("cause"));

            AprismLogRecord record = logging.getRetained().snapshot().get(0);
            assertThat(record.unit()).isEqualTo("examplemod");
            assertThat(record.level()).isEqualTo(AprismLogLevel.ERROR);
            assertThat(record.message()).isEqualTo("boom");
            assertThat(record.throwable()).isInstanceOf(IllegalStateException.class);
            assertThat(record.render()).contains("ERROR examplemod - boom");
        }

        @Test
        void closedFacilityStopsEmitting() {
            AprismLogging logging = new AprismLogging();
            logging.getLogger("unit").info("before");
            logging.close();
            logging.getLogger("unit").info("after");

            assertThat(logging.isClosed()).isTrue();
            assertThat(logging.getRetained().size()).isEqualTo(1);
        }
    }
}
