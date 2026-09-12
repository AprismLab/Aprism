package com.aprism.conformance.probe;

import java.util.List;
import java.util.Set;

import com.aprism.api.commands.CommandSpec;
import com.aprism.conformance.CoverageMatrix;
import com.aprism.conformance.Probe;
import com.aprism.conformance.ProbeResult;
import com.aprism.loader.hostinterop.AdapterReport;
import com.aprism.loader.hostinterop.HostCapability;
import com.aprism.loader.hostinterop.HostDescriptor;
import com.aprism.loader.hostinterop.HostDiscovery;
import com.aprism.loader.hostinterop.InputAdapter;
import com.aprism.loader.hostinterop.SettingsGuiAdapter;
import com.aprism.loader.livectx.LiveContext;

/**
 * Host interop probe (v26.9-Alpha.7): host discovery with capability
 * routing for commands, key input, and the settings-GUI surface, and
 * fail-closed behaviour when a capability has no providing host.
 *
 * @author BlockConnect@StarsailsClover
 */
public final class HostInteropProbe implements Probe {
    //GitHub@NDBlockConnect | BlockConnect@StarsailsClover

    @Override
    public ProbeResult run() {
        CoverageMatrix.Cell cell = new CoverageMatrix.Cell("input",
                "host discovery + command/input/settings adapters", "unit",
                CoverageMatrix.Status.CONTRACT_ONLY,
                "executed by ConformanceKit on every run; live host adapters "
                        + "remain the v26.10 milestone");
        try {
            HostDiscovery discovery = new HostDiscovery();
            discovery.host(new HostDescriptor("conformance-host",
                            HostDescriptor.Kind.EMBEDDER, LiveContext.Side.CLIENT,
                            Set.of(HostCapability.COMMANDS, HostCapability.KEY_INPUT,
                                    HostCapability.SETTINGS_GUI)))
                    .commands(specs -> new AdapterReport(
                            specs.stream().map(CommandSpec::name).toList(), List.of()))
                    .input(requests -> new AdapterReport(
                            requests.stream().map(InputAdapter.KeyBindingRequest::id)
                                    .toList(), List.of()))
                    .settingsGui(request -> new AdapterReport(
                            List.of(request.title()), List.of()))
                    .register();

            AdapterReport commands = discovery.registerCommands(
                    List.of(new CommandSpec("conformance_cmd", "d", new Object())));
            AdapterReport keys = discovery.registerKeyBindings(
                    List.of(new InputAdapter.KeyBindingRequest("conformance:key",
                            "K", "conformance")));
            AdapterReport gui = discovery.presentSettings(
                    new SettingsGuiAdapter.SettingsScreenRequest("Conformance",
                            List.of("general")));

            boolean failClosed = false;
            try {
                HostDiscovery bare = new HostDiscovery();
                bare.registerKeyBindings(List.of());
            } catch (HostDiscovery.CapabilityUnavailableException missing) {
                failClosed = missing.capability() == HostCapability.KEY_INPUT;
            }

            boolean pass = commands.acceptedIds().equals(List.of("conformance_cmd"))
                    && keys.acceptedIds().equals(List.of("conformance:key"))
                    && gui.acceptedIds().equals(List.of("Conformance"))
                    && failClosed
                    && discovery.capabilities(LiveContext.Side.CLIENT)
                            .contains(HostCapability.SETTINGS_GUI);
            return new ProbeResult(cell, pass, discovery.toJson());
        } catch (Throwable t) {
            return new ProbeResult(cell, false, t.toString());
        }
    }
}
