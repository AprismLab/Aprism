package com.aprism.loader.settings;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import com.aprism.manifest.AprismManifest;
import com.aprism.manifest.SettingDeclaration;
import com.aprism.manifest.SettingsDeclarationReader;

/**
 * Central registry of per-mod settings (v26.2-Alpha.3, goal #7 part 2).
 * During mod loading, every loaded mod's manifest setting declarations are
 * registered here; user values persist as one JSON file per mod under
 * {@code <game-root>/config/aprism-settings/<modid>.json}. Persistence is
 * fail-safe: a read or write error is logged and swallowed so a broken
 * config file never blocks the game.
 *
 * @author BlockConnect@StarsailsClover
 */
public final class SettingsRegistry {

    private static final Logger LOG = Logger.getLogger(SettingsRegistry.class.getName());

    private final Map<String, ModSettings> settingsByMod = new LinkedHashMap<>();
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private Path storageDir;

    /**
     * Sets the directory used for persisted settings files and loads any
     * existing files over the registered defaults.
     *
     * @param dir the settings storage directory (created on first persist)
     */
    public void bindStorage(Path dir) {
        this.storageDir = dir;
        for (ModSettings settings : settingsByMod.values()) {
            loadFromDisk(settings);
        }
    }

    /**
     * Registers the setting declarations of one mod from its manifest and
     * returns the mod's settings store. Existing registrations are kept and
     * only missing declarations are added, so repeated loads do not reset
     * user-modified values.
     *
     * @param manifest the mod manifest
     * @return the mod settings store (declarations may be empty)
     */
    public ModSettings register(AprismManifest manifest) {
        String modId = manifest.id();
        ModSettings settings = settingsByMod.computeIfAbsent(modId, ModSettings::new);
        List<SettingDeclaration> declarations = SettingsDeclarationReader.read(manifest);
        for (SettingDeclaration declaration : declarations) {
            if (!settings.getDeclarations().containsKey(declaration.key())) {
                settings.declare(declaration);
            }
        }
        if (storageDir != null) {
            loadFromDisk(settings);
        }
        return settings;
    }

    /**
     * @param modId the mod id
     * @return the mod settings store, or null when the mod registered none
     */
    public ModSettings get(String modId) {
        return settingsByMod.get(modId);
    }

    /**
     * @return every registered mod settings store
     */
    public List<ModSettings> getAll() {
        return List.copyOf(settingsByMod.values());
    }

    /**
     * @return the number of mods with registered settings
     */
    public int size() {
        return settingsByMod.size();
    }

    /**
     * Persists every dirty mod settings store to disk.
     */
    public void persistAll() {
        if (storageDir == null) {
            return;
        }
        for (ModSettings settings : settingsByMod.values()) {
            if (settings.isDirty()) {
                writeToDisk(settings);
            }
        }
    }

    /**
     * Persists a single mod settings store regardless of its dirty flag.
     *
     * @param modId the mod id
     */
    public void persist(String modId) {
        ModSettings settings = settingsByMod.get(modId);
        if (settings != null && storageDir != null) {
            writeToDisk(settings);
        }
    }

    /**
     * Drops all registered settings (runtime shutdown).
     */
    public void clear() {
        settingsByMod.clear();
        storageDir = null;
    }

    private void loadFromDisk(ModSettings settings) {
        Path file = settingsFile(settings.getModId());
        if (!Files.isRegularFile(file)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            Map<String, Object> persisted = gson.fromJson(reader,
                    new TypeToken<Map<String, Object>>() { }.getType());
            settings.applyPersisted(persisted);
            settings.markClean();
        } catch (IOException | RuntimeException e) {
            LOG.warning("Could not read settings for " + settings.getModId()
                    + "; using defaults: " + e.getMessage());
        }
    }

    private void writeToDisk(ModSettings settings) {
        Path file = settingsFile(settings.getModId());
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                gson.toJson(settings.snapshot(), writer);
            }
            settings.markClean();
        } catch (IOException e) {
            LOG.warning("Could not persist settings for " + settings.getModId()
                    + ": " + e.getMessage());
        }
    }

    private Path settingsFile(String modId) {
        return storageDir.resolve(modId + ".json");
    }
}
