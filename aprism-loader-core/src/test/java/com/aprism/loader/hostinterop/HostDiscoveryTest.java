package com.aprism.loader.hostinterop;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Set;

import com.aprism.api.commands.CommandSpec;
import com.aprism.loader.livectx.LiveContext;

import org.junit.jupiter.api.Test;
// GitHub@NDBlockConnect | BlockConnect@StarsailsClover

/**
 * Host discovery and adapter contract tests (v26.9-Alpha.7): capability
 * routing, fail-closed capability requirements, adapter/capability
 * consistency, merged multi-host reports, and withdraw semantics.
 *
 * @author BlockConnect@StarsailsClover
 */
class HostDiscoveryTest {

    private static HostDescriptor clientHost(HostCapability... capabilities) {
        return new HostDescriptor("client-host",
                HostDescriptor.Kind.APRISM_NATIVE, LiveContext.Side.CLIENT,
                Set.of(capabilities));
    }

    private static CommandAdapter acceptingCommands() {
        return specs -> new AdapterReport(specs.stream().map(CommandSpec::name).toList(),
                List.of());
    }

    @Test
    void routesByCapabilityAndFailsClosedWhenAbsent() {
        HostDiscovery discovery = new HostDiscovery();
        discovery.host(clientHost(HostCapability.COMMANDS))
                .commands(acceptingCommands())
                .register();

        assertTrue(discovery.capabilities(LiveContext.Side.CLIENT)
                .contains(HostCapability.COMMANDS));
        assertFalse(discovery.capabilities(LiveContext.Side.CLIENT)
                .contains(HostCapability.SETTINGS_GUI));

        AdapterReport report = discovery.registerCommands(
                List.of(new CommandSpec("probe", "d", new Object())));
        assertEquals(List.of("probe"), report.acceptedIds());
        assertTrue(report.clean());

        // No KEY_INPUT host: the capability requirement fails closed.
        HostDiscovery.CapabilityUnavailableException missing =
                assertThrows(HostDiscovery.CapabilityUnavailableException.class,
                        () -> discovery.registerKeyBindings(List.of(
                                new InputAdapter.KeyBindingRequest("mod:key", "K", "cat"))));
        assertEquals(HostCapability.KEY_INPUT, missing.capability());

        HostDiscovery.CapabilityUnavailableException noGui =
                assertThrows(HostDiscovery.CapabilityUnavailableException.class,
                        () -> discovery.presentSettings(
                                new SettingsGuiAdapter.SettingsScreenRequest("t", List.of())));
        assertEquals(HostCapability.SETTINGS_GUI, noGui.capability());
    }

    @Test
    void adapterWithoutDeclaredCapabilityFailsFast() {
        HostDiscovery discovery = new HostDiscovery();
        assertThrows(IllegalArgumentException.class, () -> discovery
                .host(clientHost(HostCapability.COMMANDS))
                .input(requests -> AdapterReport.empty())
                .register());
        assertThrows(IllegalArgumentException.class, () -> discovery
                .host(clientHost())
                .settingsGui(request -> AdapterReport.empty())
                .register());
    }

    @Test
    void mergesReportsAcrossHostsAndPreservesRefusals() {
        HostDiscovery discovery = new HostDiscovery();
        discovery.host(clientHost(HostCapability.KEY_INPUT))
                .input(requests -> new AdapterReport(
                        List.of(requests.get(0).id()),
                        List.of(new AdapterReport.Refusal(requests.get(1).id(),
                                AdapterReport.Code.HOST_REJECTED, "reserved key"))))
                .register();
        discovery.host(new HostDescriptor("second-host",
                        HostDescriptor.Kind.LAUNCHER_MANAGED, LiveContext.Side.CLIENT,
                        Set.of(HostCapability.KEY_INPUT)))
                .input(requests -> new AdapterReport(
                        List.of(requests.get(1).id()), List.of()))
                .register();

        AdapterReport merged = discovery.registerKeyBindings(List.of(
                new InputAdapter.KeyBindingRequest("mod:a", "K", "cat"),
                new InputAdapter.KeyBindingRequest("mod:b", "L", "cat")));
        assertEquals(2, merged.acceptedIds().size());
        assertTrue(merged.acceptedIds().contains("mod:a"));
        assertTrue(merged.acceptedIds().contains("mod:b"));
        assertEquals(1, merged.refusals().size());
        assertFalse(merged.clean());
    }

    @Test
    void sideScopedQueriesAndWithdrawWork() {
        HostDiscovery discovery = new HostDiscovery();
        discovery.host(clientHost(HostCapability.SETTINGS_GUI))
                .settingsGui(request -> new AdapterReport(
                        List.of(request.title()), List.of()))
                .register();
        discovery.host(new HostDescriptor("server-host",
                        HostDescriptor.Kind.DEDICATED_SERVER, LiveContext.Side.SERVER,
                        Set.of(HostCapability.COMMANDS)))
                .commands(acceptingCommands())
                .register();

        assertEquals(1, discovery.capable(HostCapability.COMMANDS,
                LiveContext.Side.SERVER).size());
        assertTrue(discovery.capable(HostCapability.COMMANDS,
                LiveContext.Side.CLIENT).isEmpty());
        assertEquals(Set.of(HostCapability.COMMANDS, HostCapability.SETTINGS_GUI),
                discovery.capabilities(null));
        assertTrue(discovery.toJson().contains("server-host"));
        assertEquals(2, discovery.hostIds().size());

        discovery.withdraw("server-host");
        assertEquals(1, discovery.hostIds().size());
        assertTrue(discovery.snapshot().containsKey("client-host"));
    }

    @Test
    void settingsPresentationRoutesToGuiCapableHost() {
        HostDiscovery discovery = new HostDiscovery();
        discovery.host(clientHost(HostCapability.SETTINGS_GUI))
                .settingsGui(request -> new AdapterReport(
                        List.of(request.title()), List.of()))
                .register();
        AdapterReport report = discovery.presentSettings(
                new SettingsGuiAdapter.SettingsScreenRequest("Aprism Settings",
                        List.of("general")));
        assertEquals(List.of("Aprism Settings"), report.acceptedIds());
        assertThrows(IllegalArgumentException.class,
                () -> new SettingsGuiAdapter.SettingsScreenRequest("", List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new InputAdapter.KeyBindingRequest("noNamespace", "K", "c"));
    }
}
