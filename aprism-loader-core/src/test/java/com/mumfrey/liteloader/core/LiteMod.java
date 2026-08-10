package com.mumfrey.liteloader.core;

import java.io.File;

/**
 * LiteLoader API shim: the base {@code LiteMod} interface that every
 * LiteLoader mod implements. Provided by Aprism so that genuine LiteLoader
 * mods can be instantiated without the real LiteLoader runtime on the
 * classpath.
 *
 * <p>Mirrors the LiteLoader contract: a mod class implements {@code LiteMod}
 * (or one of its sub-interfaces), is discovered inside a {@code .litemod}
 * archive, and receives {@link #init(File)} early in the startup sequence to
 * set up its resources. The {@code File} argument is the mod's config folder.
 *
 * @author BlockConnect@StarsailsClover
 */
public interface LiteMod {

    /**
     * @return the mod name (matches the {@code name} entry in
     *         {@code litemod.json})
     */
    String getName();

    /**
     * @return the mod version string
     */
    String getVersion();

    /**
     * Called early in the startup sequence so the mod can set up its
     * resources. Mods needing fully-initialized game state should defer that
     * work (LiteLoader offers {@code InitCompleteListener} for that purpose;
     * Aprism dispatches later lifecycle phases separately).
     *
     * @param configFolder the mod's config folder
     */
    void init(File configFolder);
}
