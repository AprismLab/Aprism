package com.aprism.loader.hostinterop;

import com.aprism.loader.livectx.LiveContext;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Host discovery and capability routing (v26.9 roadmap Alpha.7). Hosts
 * register a descriptor plus the adapters they actually implement;
 * callers ask for a capability and get either a working adapter or a
 * typed {@link CapabilityUnavailableException} - never a silent no-op.
 *
 * <p>Routing is deterministic: adapters are tried in registration order
 * and the first capable host that accepts an item wins; refusals from all
 * tried hosts are merged into one report.
 *
 * @author BlockConnect@StarsailsClover
 */
public final class HostDiscovery {
    //GitHub@NDBlockConnect | BlockConnect@StarsailsClover

    private static final Logger LOG =
            Logger.getLogger(HostDiscovery.class.getName());

    /** A registered host: descriptor plus its optional adapters. */
    public static final class RegisteredHost {
        private final HostDescriptor descriptor;
        private final CommandAdapter commands;
        private final InputAdapter input;
        private final SettingsGuiAdapter settingsGui;

        RegisteredHost(HostDescriptor descriptor, CommandAdapter commands,
                InputAdapter input, SettingsGuiAdapter settingsGui) {
            this.descriptor = descriptor;
            this.commands = commands;
            this.input = input;
            this.settingsGui = settingsGui;
        }

        /** @return the host descriptor */
        public HostDescriptor descriptor() {
            return descriptor;
        }
    }

    /** Typed capability failure. */
    public static final class CapabilityUnavailableException
            extends RuntimeException {
        private final HostCapability capability;

        CapabilityUnavailableException(HostCapability capability, String message) {
            super(message);
            this.capability = capability;
        }

        /** @return the missing capability */
        public HostCapability capability() {
            return capability;
        }
    }

    /** Fluent registration of one host. */
    public final class Builder {
        private final HostDescriptor descriptor;
        private CommandAdapter commands;
        private InputAdapter input;
        private SettingsGuiAdapter settingsGui;

        Builder(HostDescriptor descriptor) {
            this.descriptor = descriptor;
        }

        /** Attaches a command adapter (requires the COMMANDS capability). */
        public Builder commands(CommandAdapter adapter) {
            this.commands = adapter;
            return this;
        }

        /** Attaches an input adapter (requires the KEY_INPUT capability). */
        public Builder input(InputAdapter adapter) {
            this.input = adapter;
            return this;
        }

        /** Attaches a settings adapter (requires SETTINGS_GUI). */
        public Builder settingsGui(SettingsGuiAdapter adapter) {
            this.settingsGui = adapter;
            return this;
        }

        /**
         * Completes registration, refusing adapters whose capability the
         * descriptor does not advertise (an adapter without its capability
         * declaration would be undiscoverable, so this fails fast).
         */
        public void register() {
            if (commands != null
                    && !descriptor.supports(HostCapability.COMMANDS)) {
                throw new IllegalArgumentException("command adapter attached "
                        + "without COMMANDS capability: " + descriptor.hostId());
            }
            if (input != null && !descriptor.supports(HostCapability.KEY_INPUT)) {
                throw new IllegalArgumentException("input adapter attached "
                        + "without KEY_INPUT capability: " + descriptor.hostId());
            }
            if (settingsGui != null
                    && !descriptor.supports(HostCapability.SETTINGS_GUI)) {
                throw new IllegalArgumentException("settings adapter attached "
                        + "without SETTINGS_GUI capability: " + descriptor.hostId());
            }
            hosts.put(descriptor.hostId(), new RegisteredHost(descriptor,
                    commands, input, settingsGui));
            LOG.info("[hostinterop] discovered host " + descriptor.hostId()
                    + " kind=" + descriptor.kind() + " capabilities="
                    + descriptor.capabilities());
        }
    }

    private final Map<String, RegisteredHost> hosts = new ConcurrentHashMap<>();

    /**
     * Begins registering a host.
     *
     * @param descriptor what the host is and can do
     * @return the fluent builder
     */
    public Builder host(HostDescriptor descriptor) {
        return new Builder(descriptor);
    }

    /** Withdraws a host (leak prevention on host shutdown). */
    public void withdraw(String hostId) {
        hosts.remove(hostId);
    }

    /** @return the discovered host ids in registration order */
    public List<String> hostIds() {
        return List.copyOf(hosts.keySet());
    }

    /**
     * @param capability the capability to query
     * @param side the side to query (null = any side)
     * @return the hosts advertising the capability
     */
    public List<HostDescriptor> capable(HostCapability capability,
            LiveContext.Side side) {
        List<HostDescriptor> out = new ArrayList<>();
        for (RegisteredHost host : hosts.values()) {
            if (host.descriptor().supports(capability)
                    && (side == null || host.descriptor().side() == side)) {
                out.add(host.descriptor());
            }
        }
        return out;
    }

    /**
     * @param side the side to aggregate (null = all)
     * @return the union of advertised capabilities for that side
     */
    public Set<HostCapability> capabilities(LiveContext.Side side) {
        EnumSet<HostCapability> union = EnumSet.noneOf(HostCapability.class);
        for (RegisteredHost host : hosts.values()) {
            if (side == null || host.descriptor().side() == side) {
                union.addAll(host.descriptor().capabilities());
            }
        }
        return Set.copyOf(union);
    }

    /**
     * Requires a capability to exist for the side.
     *
     * @param capability the capability
     * @param side the side (null = any)
     * @throws CapabilityUnavailableException when no host provides it
     */
    public void require(HostCapability capability, LiveContext.Side side) {
        if (capable(capability, side).isEmpty()) {
            throw new CapabilityUnavailableException(capability,
                    "no discovered host provides " + capability
                            + (side == null ? "" : " on " + side));
        }
    }

    /**
     * Registers commands through every capable host, merging reports.
     * Deterministic: registration order.
     *
     * @param specs the command specs
     * @return the merged report
     * @throws CapabilityUnavailableException when no host can take commands
     */
    public AdapterReport registerCommands(List<com.aprism.api.commands.CommandSpec> specs) {
        require(HostCapability.COMMANDS, null);
        List<String> accepted = new ArrayList<>();
        List<AdapterReport.Refusal> refusals = new ArrayList<>();
        for (RegisteredHost host : hosts.values()) {
            if (!host.descriptor().supports(HostCapability.COMMANDS)
                    || host.commands == null) {
                continue;
            }
            AdapterReport report = host.commands.registerCommands(specs);
            accepted.addAll(report.acceptedIds());
            refusals.addAll(report.refusals());
        }
        return new AdapterReport(accepted, refusals);
    }

    /**
     * Installs key bindings through every capable host, merging reports.
     *
     * @param requests the binding requests
     * @return the merged report
     * @throws CapabilityUnavailableException when no host can take input
     */
    public AdapterReport registerKeyBindings(List<InputAdapter.KeyBindingRequest> requests) {
        require(HostCapability.KEY_INPUT, null);
        List<String> accepted = new ArrayList<>();
        List<AdapterReport.Refusal> refusals = new ArrayList<>();
        for (RegisteredHost host : hosts.values()) {
            if (!host.descriptor().supports(HostCapability.KEY_INPUT)
                    || host.input == null) {
                continue;
            }
            AdapterReport report = host.input.registerKeyBindings(requests);
            accepted.addAll(report.acceptedIds());
            refusals.addAll(report.refusals());
        }
        return new AdapterReport(accepted, refusals);
    }

    /**
     * Presents settings through every capable host, merging reports.
     *
     * @param request the settings surface request
     * @return the merged report
     * @throws CapabilityUnavailableException when no host has a settings GUI
     */
    public AdapterReport presentSettings(SettingsGuiAdapter.SettingsScreenRequest request) {
        require(HostCapability.SETTINGS_GUI, null);
        List<String> accepted = new ArrayList<>();
        List<AdapterReport.Refusal> refusals = new ArrayList<>();
        for (RegisteredHost host : hosts.values()) {
            if (!host.descriptor().supports(HostCapability.SETTINGS_GUI)
                    || host.settingsGui == null) {
                continue;
            }
            AdapterReport report = host.settingsGui.presentSettings(request);
            accepted.addAll(report.acceptedIds());
            refusals.addAll(report.refusals());
        }
        return new AdapterReport(accepted, refusals);
    }

    /** @return compact JSON diagnostics for the discovered hosts */
    public String toJson() {
        StringBuilder sb = new StringBuilder("{\"hosts\":[");
        boolean first = true;
        for (RegisteredHost host : hosts.values()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            HostDescriptor d = host.descriptor();
            sb.append("{\"hostId\":\"").append(d.hostId())
                    .append("\",\"kind\":\"").append(d.kind())
                    .append("\",\"side\":\"").append(d.side())
                    .append("\",\"capabilities\":[");
            boolean firstCap = true;
            for (HostCapability capability : d.capabilities()) {
                if (!firstCap) {
                    sb.append(',');
                }
                firstCap = false;
                sb.append('"').append(capability).append('"');
            }
            sb.append("]}");
        }
        return sb.append("]}").toString();
    }

    /** @return an immutable snapshot of capabilities per registered host */
    public Map<String, Set<HostCapability>> snapshot() {
        Map<String, Set<HostCapability>> out = new LinkedHashMap<>();
        for (RegisteredHost host : hosts.values()) {
            out.put(host.descriptor().hostId(), host.descriptor().capabilities());
        }
        return Map.copyOf(out);
    }
}
