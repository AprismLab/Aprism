package com.aprism.loader.contentbind;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import com.aprism.loader.lowlevel.MethodHookRegistry;
import com.aprism.loader.registry.GameRegistries;

/**
 * Defers content binding until the live game has bootstrapped its vanilla
 * registries (v26.7-Alpha.1).
 *
 * <p>Binding inside the agent premain is too early (BuiltInRegistries clinit
 * fails before Minecraft main). This runner hooks
 * {@code net.minecraft.client.main.Main.main} entry via the v26.1-Alpha.8
 * method-hook machinery and, from a one-shot daemon thread 3 seconds later,
 * performs the bind - by then vanilla bootstrap has run and the registries
 * are still pre-freeze writable. Server parity is a later milestone.
 *
 * @author BlockConnect@StarsailsClover
 */
public final class ContentBindingRunner {

    private static final Logger LOG = Logger.getLogger("aprism.contentbind");

    private static final String MAIN_CLASS = "net.minecraft.client.main.Main";
    private static final String MAIN_METHOD = "main";
    private static final String MAIN_DESCRIPTOR = "([Ljava/lang/String;)V";
    private static final long DELAY_MS = 3000;

    private static final AtomicBoolean SCHEDULED = new AtomicBoolean(false);

    private ContentBindingRunner() {
    }

    /**
     * Registers the deferred binding (idempotent). Never blocks or throws.
     *
     * @param gameRegistries the content registries to bind
     * @param remapProfile   whether the runtime selected a remapped profile
     */
    public static void submit(GameRegistries gameRegistries, boolean remapProfile) {
        if (!SCHEDULED.compareAndSet(false, true)) {
            return;
        }
        try {
            MethodHookRegistry.register(MAIN_CLASS, MAIN_METHOD, MAIN_DESCRIPTOR, () -> {
                Thread t = new Thread(() -> {
                    try {
                        Thread.sleep(DELAY_MS);
                    } catch (InterruptedException ignored) {
                        return;
                    }
                    bindNow(gameRegistries, remapProfile);
                }, "aprism-content-bind");
                t.setDaemon(true);
                t.start();
            });
            LOG.info("Content binding deferred until after game bootstrap");
        } catch (Throwable t) {
            SCHEDULED.set(false);
            LOG.warning("Failed to schedule content binding: " + t);
        }
    }

    /**
     * Performs the bind immediately (tests / manual fallback).
     */
    public static List<GameContentBindingInstaller.BindingResult> bindNow(
            GameRegistries gameRegistries, boolean remapProfile) {
        return bindNow(gameRegistries, remapProfile, null);
    }

    /**
     * Performs binding with optional official-name mappings for REMAPPED
     * profiles.
     *
     * @param gameRegistries content registries
     * @param remapProfile whether the runtime selected REMAPPED
     * @param officialMappings Mojang client mappings, or null
     * @return per-entry results
     */
    public static List<GameContentBindingInstaller.BindingResult> bindNow(
            GameRegistries gameRegistries, boolean remapProfile,
            OfficialMappings officialMappings) {
        GameContentBindingInstaller installer =
                new GameContentBindingInstaller(gameRegistries);
        installer.setRemapProfile(remapProfile);
        installer.setOfficialMappings(officialMappings);
        List<GameContentBindingInstaller.BindingResult> results = installer.bindAll();
        long ok = results.stream().filter(
                GameContentBindingInstaller.BindingResult::ok).count();
        LOG.info("Content binding: " + ok + "/" + results.size()
                + " unit(s) bound to the live registries");
        return results;
    }
}
