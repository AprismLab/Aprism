package com.aprism.loader.bedrock;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import com.aprism.loader.LoadedBedrockModContainer;

/**
 * Stages native libraries from {@code .abe} mod archives onto disk so a
 * {@link NativeInjector} can load them (FACT.md 9.8 / 9.16).
 *
 * <p>The native injector operates on real file paths, not archive entries, so
 * before injection each planned native library must be extracted to a stable
 * on-disk staging directory. This class performs that extraction:
 * <ul>
 *   <li>Staging root: {@code <gameRoot>/aprism_native_stage/}</li>
 *   <li>Each library: {@code aprism_native_stage/<modId>/<entryFileName>}</li>
 * </ul>
 *
 * <p>Extraction is idempotent (REPLACE_EXISTING), so a repeated load simply
 * refreshes the staged copies. Only the entries named by the plan's actions
 * are staged; unrelated archive contents are left untouched.
 *
 * @author BlockConnect@StarsailsClover
 */
public final class BedrockNativeStager {

    /** Staging subdirectory under the game root. */
    public static final String STAGE_DIR_NAME = "aprism_native_stage";

    /**
     * The outcome of staging: maps each planned entry path to the staged
     * on-disk library path. Empty on failure.
     */
    public record StagingResult(boolean success, Map<String, Path> stagedLibraries, String message) {
        /** @return true if staging succeeded */
        public boolean isSuccess() {
            return success;
        }
    }

    /**
     * Stages every native library referenced by the given plan's actions.
     *
     * @param gameRoot      the BE game root (staging lives under it)
     * @param plan          the feasible injection plan whose actions to stage
     * @param modsById      map of mod id to its container (for the archive path)
     * @return the staging result; empty map and a message on any failure
     */
    public StagingResult stage(Path gameRoot, BedrockInjectionPlan.Plan plan,
                               Map<String, LoadedBedrockModContainer> modsById) {
        Objects.requireNonNull(gameRoot, "gameRoot must not be null");
        Objects.requireNonNull(plan, "plan must not be null");
        Objects.requireNonNull(modsById, "modsById must not be null");
        if (!plan.isFeasible()) {
            return new StagingResult(false, Map.of(), "plan is not feasible");
        }

        Path stageRoot = gameRoot.resolve(STAGE_DIR_NAME);
        Map<String, Path> staged = new LinkedHashMap<>();
        try {
            Files.createDirectories(stageRoot);
        } catch (IOException e) {
            return new StagingResult(false, Map.of(),
                    "failed to create staging directory: " + stageRoot);
        }

        for (BedrockInjectionPlan.InjectionAction action : plan.actions()) {
            LoadedBedrockModContainer mod = modsById.get(action.modId());
            if (mod == null || mod.getSourcePath() == null) {
                return new StagingResult(false, Map.of(),
                        "no source archive for mod: " + action.modId());
            }
            Path target = stageRoot.resolve(action.modId())
                    .resolve(fileNameOf(action.entryPath()));
            try {
                Files.createDirectories(target.getParent());
                extractEntry(mod.getSourcePath(), action.entryPath(), target);
            } catch (IOException e) {
                return new StagingResult(false, Map.of(),
                        "failed to stage " + action.entryPath() + " from "
                                + mod.getSourcePath() + ": " + e.getMessage());
            }
            staged.put(action.entryPath(), target);
        }
        return new StagingResult(true, staged, null);
    }

    /**
     * Extracts a single entry from the source archive to the target path.
     */
    private static void extractEntry(Path archive, String entryPath, Path target) throws IOException {
        try (FileSystem fs = FileSystems.newFileSystem(archive, (ClassLoader) null)) {
            Path src = fs.getPath(entryPath);
            if (!Files.exists(src)) {
                throw new IOException("entry not found in archive: " + entryPath);
            }
            try (InputStream is = Files.newInputStream(src)) {
                Files.copy(is, target, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    /**
     * Returns the file name component of an entry path
     * ({@code native/windows/a.dll} -> {@code a.dll}).
     */
    private static String fileNameOf(String entryPath) {
        int idx = entryPath.replace('\\', '/').lastIndexOf('/');
        return idx >= 0 ? entryPath.substring(idx + 1) : entryPath;
    }
}
