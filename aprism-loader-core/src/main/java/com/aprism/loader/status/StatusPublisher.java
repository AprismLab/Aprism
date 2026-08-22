package com.aprism.loader.status;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import com.aprism.loader.LoadReport;
import com.aprism.loader.modmenu.ModListEntry;
import com.aprism.loader.modmenu.ModListRegistry;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Publishes a machine-readable loader status file for external tools
 * (v26.6-Alpha.2, MDL deep integration).
 *
 * <p>After every load milestone the runtime writes
 * {@code <gameRoot>/aprism-status.json} containing the running Aprism
 * version, the target Minecraft version, the current lifecycle phase, and
 * the per-unit load outcomes (mods, extensions, failures). Launcher tooling
 * (MDL diagnose), the installer's first-run report, and support workflows
 * read this single file instead of parsing game logs.
 *
 * <p>Publishing is fail-safe: IO errors are logged and swallowed so a
 * read-only or missing game root never breaks the boot. The file is written
 * atomically (temp file + move) so concurrent readers never observe a
 * half-written document.
 *
 * <p>Schema ({@code aprism.status/v1}):
 * <pre>{@code
 * {
 *   "schemaVersion": "aprism.status/v1",
 *   "aprismVersion": "v26.6-Alpha.2",
 *   "mcEdit": "JE",
 *   "mcVersion": "26.2",
 *   "generatedAt": "2026-08-21T12:00:00Z",
 *   "phase": "LOADED",
 *   "okCount": 3,
 *   "failureCount": 0,
 *   "units": [
 *     {"kind": "mod", "id": "examplemod", "version": "1.0.0",
 *      "loaderKey": "", "state": "LOADED", "durationMs": 12}
 *   ]
 * }
 * }</pre>
 *
 * @author BlockConnect@StarsailsClover
 */
public final class StatusPublisher {

    /** The logger for fail-safe IO diagnostics. */
    private static final Logger LOG = Logger.getLogger("aprism.status");

    /** The schema identifier written into every published document. */
    public static final String SCHEMA_VERSION = "aprism.status/v1";

    /** The fixed file name published under the game root. */
    public static final String FILE_NAME = "aprism-status.json";

    /** Shared Gson instance; the document is small so pretty printing is free. */
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private StatusPublisher() {
    }

    /**
     * Builds a status snapshot from the current runtime state.
     *
     * @param aprismVersion the running Aprism version (may be null)
     * @param mcEdit        the target edition (JE/BE, may be null)
     * @param mcVersion     the target Minecraft version (may be null)
     * @param phase         the lifecycle phase label (e.g. {@code LOADED},
     *                      {@code SHUTDOWN})
     * @param modList       the queryable mod list (may be null)
     * @param report        the load report with per-unit timings (may be null)
     * @return the snapshot ready to publish
     */
    public static Map<String, Object> buildSnapshot(String aprismVersion, String mcEdit,
            String mcVersion, String phase, ModListRegistry modList, LoadReport report) {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("schemaVersion", SCHEMA_VERSION);
        doc.put("aprismVersion", aprismVersion == null ? "" : aprismVersion);
        doc.put("mcEdit", mcEdit == null ? "" : mcEdit);
        doc.put("mcVersion", mcVersion == null ? "" : mcVersion);
        doc.put("generatedAt", Instant.now().toString());
        doc.put("phase", phase == null ? "" : phase);

        List<Map<String, Object>> units = new ArrayList<>();
        int okCount = 0;
        int failureCount = 0;
        if (modList != null) {
            for (ModListEntry entry : modList.getAll()) {
                Map<String, Object> unit = new LinkedHashMap<>();
                unit.put("kind", entry.kind());
                unit.put("id", entry.id());
                unit.put("version", entry.version());
                unit.put("loaderKey", entry.loaderKey());
                unit.put("state", entry.state() == null ? "" : entry.state().name());
                units.add(unit);
                if (entry.isFailed()) {
                    failureCount++;
                } else {
                    okCount++;
                }
            }
        }
        // Enrich durations from the load report when available.
        if (report != null) {
            Map<String, Long> durationByUnit = new LinkedHashMap<>();
            for (LoadReport.Entry entry : report.entries()) {
                durationByUnit.put(entry.kind() + ":" + entry.id(), entry.durationMs());
            }
            for (Map<String, Object> unit : units) {
                Long duration = durationByUnit.get(unit.get("kind") + ":" + unit.get("id"));
                if (duration != null) {
                    unit.put("durationMs", duration);
                }
            }
        }
        doc.put("okCount", okCount);
        doc.put("failureCount", failureCount);
        doc.put("units", units);
        return doc;
    }

    /**
     * Publishes the status snapshot to {@code <gameRoot>/aprism-status.json}.
     * Fail-safe: any IO error is logged at FINE level and swallowed.
     *
     * @param gameRoot the game instance root
     * @param snapshot the snapshot to write
     * @return the published file path, or null when publishing failed or the
     *         game root is null
     */
    public static Path publish(Path gameRoot, Map<String, Object> snapshot) {
        if (gameRoot == null || snapshot == null) {
            return null;
        }
        try {
            Files.createDirectories(gameRoot);
            Path target = gameRoot.resolve(FILE_NAME);
            Path tmp = gameRoot.resolve(FILE_NAME + ".tmp");
            String json = GSON.toJson(snapshot);
            Files.writeString(tmp, json);
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
            return target;
        } catch (IOException e) {
            // Atomic move can fall back on some filesystems; retry non-atomic.
            try {
                Path target = gameRoot.resolve(FILE_NAME);
                Path tmp = gameRoot.resolve(FILE_NAME + ".tmp");
                if (Files.exists(tmp)) {
                    Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
                    return target;
                }
            } catch (IOException ignored) {
                // fall through to the log below
            }
            LOG.fine("StatusPublisher: failed to publish status: " + e.getMessage());
            return null;
        }
    }

    /**
     * Removes the status file (best-effort). Used when a clean shutdown
     * should not leave a stale LOADED snapshot behind.
     *
     * @param gameRoot the game instance root (may be null)
     */
    public static void unpublish(Path gameRoot) {
        if (gameRoot == null) {
            return;
        }
        try {
            Files.deleteIfExists(gameRoot.resolve(FILE_NAME));
        } catch (IOException e) {
            LOG.fine("StatusPublisher: failed to remove status: " + e.getMessage());
        }
    }
}
