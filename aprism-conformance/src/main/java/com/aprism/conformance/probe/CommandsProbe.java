package com.aprism.conformance.probe;

import com.aprism.api.commands.CommandSpec;
import com.aprism.conformance.CoverageMatrix;
import com.aprism.conformance.Probe;
import com.aprism.conformance.ProbeResult;
import com.aprism.loader.commands.CommandRegistrationImpl;

/**
 * Commands contract probe (v26.9-Alpha.1): the loader-level registration
 * surface accepts specs inside the open window, freezes, and reports the
 * registered set. The live-game counterpart is evidenced by the v26.7
 * 26.2 smoke line.
 *
 * @author BlockConnect@StarsailsClover
 */
public final class CommandsProbe implements Probe {
    //GitHub@NDBlockConnect | BlockConnect@StarsailsClover

    @Override
    public ProbeResult run() {
        CoverageMatrix.Cell cell = new CoverageMatrix.Cell("commands",
                "registration surface", "unit+live-ref",
                CoverageMatrix.Status.VERIFIED_LIVE,
                "live: v26.7 26.2 smoke (FACT); contract re-run by kit");
        try {
            CommandRegistrationImpl registration = new CommandRegistrationImpl();
            registration.openWindow();
            CommandSpec spec = new CommandSpec("conformance_ping",
                    "conformance probe command", new Object());
            registration.register(spec);
            registration.freezeWindow();
            boolean stored = registration.registeredCommands().size() == 1
                    && "conformance_ping".equals(
                            registration.registeredCommands().get(0).name());
            boolean frozen = registration.isFrozen();
            registration.clear();
            boolean cleared = registration.registeredCommands().isEmpty();
            boolean pass = stored && frozen && cleared;
            return new ProbeResult(cell, pass, "stored=" + stored
                    + " frozen=" + frozen + " cleared=" + cleared);
        } catch (Throwable t) {
            return new ProbeResult(cell, false, t.toString());
        }
    }
}
