package com.aprism.loader.modmenu;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Runtime-queryable registry backing the native Aprism mod list
 * (v26.2-Alpha.2, goal #7). After loading, the registry holds one immutable
 * {@link ModListEntry} per unit (every mod and every extension, including
 * failed ones), ready for the future in-game mod menu in the style of
 * Fabric Mod Menu / the NeoForge native mod menu.
 *
 * <p>The registry is rebuilt from scratch on every load pass; it is never
 * incrementally mutated, so consumers always see a consistent snapshot.
 *
 * @author BlockConnect@StarsailsClover
 */
public final class ModListRegistry {

    private final Map<String, ModListEntry> entriesById = new LinkedHashMap<>();

    /**
     * Replaces the entire registry contents.
     *
     * @param entries the new entries (keyed by id)
     */
    public void rebuild(Map<String, ModListEntry> entries) {
        entriesById.clear();
        entriesById.putAll(entries);
    }

    /**
     * Registers or replaces a single entry.
     *
     * @param entry the entry, keyed by its id
     */
    public void register(ModListEntry entry) {
        if (entry != null && entry.id() != null) {
            entriesById.put(entry.id(), entry);
        }
    }

    /**
     * Looks up an entry by id.
     *
     * @param id the unit id
     * @return the entry, or empty when unknown
     */
    public Optional<ModListEntry> get(String id) {
        return Optional.ofNullable(entriesById.get(id));
    }

    /**
     * @return all entries sorted by id
     */
    public List<ModListEntry> getAll() {
        List<ModListEntry> out = new ArrayList<>(entriesById.values());
        out.sort(Comparator.comparing(ModListEntry::id));
        return out;
    }

    /**
     * @return all entries that are mods (kind {@code "mod"})
     */
    public List<ModListEntry> getMods() {
        return entriesById.values().stream()
                .filter(e -> !e.isExtension())
                .sorted(Comparator.comparing(ModListEntry::id))
                .toList();
    }

    /**
     * @return all entries that are extensions (kind {@code "extension"})
     */
    public List<ModListEntry> getExtensions() {
        return entriesById.values().stream()
                .filter(ModListEntry::isExtension)
                .sorted(Comparator.comparing(ModListEntry::id))
                .toList();
    }

    /**
     * @return all entries whose state is FAILED
     */
    public List<ModListEntry> getFailed() {
        return entriesById.values().stream()
                .filter(ModListEntry::isFailed)
                .sorted(Comparator.comparing(ModListEntry::id))
                .toList();
    }

    /**
     * @return the number of registered entries
     */
    public int size() {
        return entriesById.size();
    }

    /**
     * Drops all entries (runtime shutdown).
     */
    public void clear() {
        entriesById.clear();
    }
}
