package com.aprism.loader.commands;

import java.util.List;
import java.util.logging.Logger;

import com.aprism.api.commands.CommandRegistration;
import com.aprism.api.commands.CommandSpec;

/**
 * Bridges registered {@link CommandSpec} entries into the Minecraft command
 * dispatcher (v26.5-Alpha.4).
 *
 * <p>The v26.3-Alpha.8 command registration surface is a registration-only
 * contract: mods declare commands by name + description + handler, and the
 * loader freezes the list at the COMPLETE phase. This installer is the bridge
 * that takes the frozen command list and binds each command to the real
 * Minecraft command dispatcher through a platform-supplied
 * {@link CommandDispatcherBridge}.
 *
 * <p>The bridge is supplied by the platform adapter layer (which knows the
 * running MC version's command dispatcher API). The loader core never
 * references Minecraft command classes directly, keeping it version-agnostic.
 *
 * <p>Binding happens when {@link #bindCommands()} is called — typically from
 * a method hook on the MC {@code Commands} constructor or
 * {@code Commands#sendStartupCommands} (installed by the
 * {@link com.aprism.loader.gameevent.GameEventHookInstaller}). If no bridge is
 * attached, binding is a no-op (fail-closed: commands are registered but
 * never dispatched).
 *
 * <p>All binding is fail-safe: a throwing bridge binding isolates only the
 * failing command; the remaining commands still bind.
 *
 * @author BlockConnect@StarsailsClover
 */
public final class CommandBindingInstaller {

    private static final Logger LOG = Logger.getLogger("aprism.commands");

    private final CommandRegistration registration;
    private CommandDispatcherBridge bridge;

    /**
     * @param registration the command registration surface holding the frozen
     *                     command list
     */
    public CommandBindingInstaller(CommandRegistration registration) {
        if (registration == null) {
            throw new IllegalArgumentException("registration must not be null");
        }
        this.registration = registration;
    }

    /**
     * Attaches the platform-supplied command dispatcher bridge. The bridge is
     * responsible for translating {@link CommandSpec} entries into the real
     * MC command dispatcher registrations.
     *
     * @param bridge the dispatcher bridge, or null to detach
     */
    public void setBridge(CommandDispatcherBridge bridge) {
        this.bridge = bridge;
    }

    /**
     * @return whether a dispatcher bridge is currently attached
     */
    public boolean isBridgeAttached() {
        return bridge != null;
    }

    /**
     * Binds all registered commands to the MC command dispatcher through the
     * attached bridge. Called after the registration window freezes (COMPLETE
     * phase). Each command is bound individually so a failing binding
     * isolates only that command.
     *
     * @return the number of commands successfully bound
     */
    public int bindCommands() {
        if (bridge == null) {
            LOG.info("No command dispatcher bridge attached; "
                    + registration.registeredCommands().size()
                    + " command(s) registered but not bound");
            return 0;
        }
        List<CommandSpec> commands = registration.registeredCommands();
        int bound = 0;
        for (CommandSpec spec : commands) {
            try {
                bridge.bind(spec);
                bound++;
            } catch (RuntimeException e) {
                LOG.warning("Failed to bind command '" + spec.name()
                        + "': " + e.getMessage());
            }
        }
        if (bound > 0) {
            LOG.info("Bound " + bound + "/" + commands.size()
                    + " command(s) to the MC command dispatcher");
        }
        return bound;
    }

    /**
     * Unbinds all commands through the bridge (if attached) and clears
     * state. Called on runtime shutdown.
     */
    public void unbindAll() {
        if (bridge != null) {
            try {
                bridge.unbindAll();
            } catch (RuntimeException e) {
                LOG.warning("Failed to unbind commands: " + e.getMessage());
            }
        }
        bridge = null;
    }
}
