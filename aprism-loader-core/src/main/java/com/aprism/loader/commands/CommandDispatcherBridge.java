package com.aprism.loader.commands;

import com.aprism.api.commands.CommandSpec;

/**
 * Platform-supplied bridge that binds {@link CommandSpec} entries to the
 * Minecraft command dispatcher (v26.5-Alpha.4).
 *
 * <p>The implementation is provided by the platform adapter layer (which
 * knows the running MC version's command dispatcher API, e.g.
 * {@code com.mojang.brigadier.CommandDispatcher} in MC 26.x). The loader
 * core never references MC command classes directly.
 *
 * <p>Implementations must be fail-safe: a throwing {@link #bind} call must
 * not prevent subsequent commands from binding. The installer catches
 * {@code RuntimeException} per-command, but bridges should avoid throwing
 * checked exceptions.
 *
 * @author BlockConnect@StarsailsClover
 */
public interface CommandDispatcherBridge {

    /**
     * Binds a single command spec to the MC command dispatcher.
     *
     * @param spec the command specification (name, description, handler)
     * @throws RuntimeException if the binding fails (caught by the installer)
     */
    void bind(CommandSpec spec);

    /**
     * Unbinds all previously bound commands. Called on runtime shutdown.
     */
    void unbindAll();
}
