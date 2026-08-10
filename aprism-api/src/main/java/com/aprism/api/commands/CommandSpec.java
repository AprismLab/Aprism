package com.aprism.api.commands;

/**
 * A loader-level command registration (Fabric
 * {@code CommandRegistrationCallback} parity, v26.3-Alpha.8). A command is
 * declared by name with a description and an execution handler; the game
 * side binds the handler to the real command dispatcher when commands are
 * built, so the loader-level spec stays independent of any dispatcher API.
 *
 * @param name the command name (first token)
 * @param description a human-readable description (may be blank)
 * @param handler the execution handler object, owned by the registering mod
 * @author BlockConnect@StarsailsClover
 */
public record CommandSpec(String name, String description, Object handler) {

    /**
     * Canonical compact constructor: validates the command name and handler.
     */
    public CommandSpec {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("command name must be non-blank");
        }
        if (handler == null) {
            throw new IllegalArgumentException("command handler must be non-null");
        }
        description = description == null ? "" : description;
    }
}
