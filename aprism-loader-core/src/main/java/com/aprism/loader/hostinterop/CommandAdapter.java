package com.aprism.loader.hostinterop;

import com.aprism.api.commands.CommandSpec;

import java.util.List;

/**
 * Command registration adapter contract (v26.9 roadmap Alpha.7): the
 * host-agnostic shape of "put these commands into your dispatcher". A host
 * that advertises {@link HostCapability#COMMANDS} implements it; the
 * existing {@code CommandDispatcherBridge} remains the live-binding
 * detail behind native hosts.
 *
 * @author BlockConnect@StarsailsClover
 */
@FunctionalInterface
public interface CommandAdapter {
    //GitHub@NDBlockConnect | BlockConnect@StarsailsClover

    /**
     * Registers the specs into the host dispatcher.
     *
     * @param specs the command specs
     * @return the per-spec registration report
     */
    AdapterReport registerCommands(List<CommandSpec> specs);
}
