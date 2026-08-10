package com.aprism.loader.commands;

import com.aprism.api.commands.CommandRegistration;
import com.aprism.api.commands.CommandSpec;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Thread-safe implementation of {@link CommandRegistration} with a
 * one-shot registration window (v26.3-Alpha.8, Fabric parity). The window
 * opens when the INIT phase begins and freezes when the COMPLETE phase
 * fires; registrations outside the window are rejected fail-closed and
 * duplicate command names are refused.
 *
 * @author BlockConnect@StarsailsClover
 */
public final class CommandRegistrationImpl implements CommandRegistration {

    private final List<CommandSpec> commands = new CopyOnWriteArrayList<>();
    private volatile boolean windowOpen;
    private volatile boolean frozen;

    /**
     * Opens the registration window (INIT phase).
     */
    public void openWindow() {
        this.windowOpen = true;
    }

    /**
     * Freezes the registration window (COMPLETE phase).
     */
    public void freezeWindow() {
        this.windowOpen = false;
        this.frozen = true;
    }

    /**
     * @return whether the window was frozen after being open
     */
    public boolean isFrozen() {
        return frozen;
    }

    @Override
    public void register(CommandSpec spec) {
        if (spec == null) {
            throw new IllegalArgumentException("command spec must be non-null");
        }
        if (!windowOpen) {
            throw new IllegalStateException(
                    "command registration outside the registration window: " + spec.name());
        }
        synchronized (commands) {
            for (CommandSpec existing : commands) {
                if (existing.name().equals(spec.name())) {
                    throw new IllegalArgumentException("duplicate command name: " + spec.name());
                }
            }
            commands.add(spec);
        }
    }

    @Override
    public boolean isWindowOpen() {
        return windowOpen;
    }

    @Override
    public List<CommandSpec> registeredCommands() {
        return List.copyOf(commands);
    }

    @Override
    public void clear() {
        commands.clear();
        windowOpen = false;
        frozen = false;
    }
}
