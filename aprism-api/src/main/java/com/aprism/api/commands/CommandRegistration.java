package com.aprism.api.commands;

import java.util.List;

/**
 * Command registration surface providing Fabric
 * {@code CommandRegistrationCallback} parity (v26.3-Alpha.8). Mods register
 * commands inside the registration window (opened when the INIT phase
 * begins, frozen once the COMPLETE phase fires); registrations outside the
 * window are rejected fail-closed, mirroring the one-shot callback model.
 *
 * @author BlockConnect@StarsailsClover
 */
public interface CommandRegistration {

    /**
     * Registers a command.
     *
     * @param spec the command specification
     * @throws IllegalArgumentException if the spec is invalid or its name is
     *                                  already registered
     * @throws IllegalStateException if the registration window is not open
     */
    void register(CommandSpec spec);

    /**
     * @return whether the registration window is currently open
     */
    boolean isWindowOpen();

    /**
     * @return the registered commands in registration order
     */
    List<CommandSpec> registeredCommands();

    /**
     * Removes all registered commands and closes the window. Called by the
     * loader on shutdown.
     */
    void clear();
}
