package com.aprism.loader.reginterop;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.aprism.api.registry.BlockContent;
import com.aprism.api.registry.EntityContent;
import com.aprism.api.registry.ItemContent;
import com.aprism.api.registry.ResourceKey;
import com.aprism.loader.registry.GameRegistries;

/**
 * The interop pass (v26.9 roadmap Alpha.4): discovers
 * {@link ContentProvider}s via ServiceLoader, validates their
 * contributions through the schema sink, lands accepted entries into the
 * runtime {@link GameRegistries}, and reports every outcome. The actual
 * live-game binding stays in {@code GameContentBindingInstaller}; this
 * layer owns the schema, the provider discovery, and the diagnostics.
 *
 * @author BlockConnect@StarsailsClover
 */
public final class RegistryInteropService {
    //GitHub@NDBlockConnect | BlockConnect@StarsailsClover

    /** Outcome of one interop pass. */
    public record ContributionReport(List<String> providers,
            List<RegistrySchema.Entry> accepted,
            List<String> rejected,
            List<String> providerFailures) {

        /**
         * @return the report as compact JSON
         */
        public String toJson() {
            StringBuilder sb = new StringBuilder("{\"providers\":[");
            appendQuoted(sb, providers);
            sb.append("],\"accepted\":").append(accepted.size())
                    .append(",\"rejected\":").append(rejected.size())
                    .append(",\"providerFailures\":").append(providerFailures.size())
                    .append("}");
            return sb.toString();
        }

        private static void appendQuoted(StringBuilder sb, List<String> values) {
            for (int i = 0; i < values.size(); i++) {
                if (i > 0) {
                    sb.append(',');
                }
                sb.append('"').append(values.get(i).replace("\"", "\\\""))
                        .append('"');
            }
        }
    }

    private final GameRegistries registries;
    private final List<ContentProvider> providers;

    /**
     * @param registries the runtime registries to land entries into
     * @param providers explicit provider list (tests, embedders)
     */
    public RegistryInteropService(GameRegistries registries,
            List<ContentProvider> providers) {
        this.registries = registries;
        this.providers = List.copyOf(providers);
    }

    /**
     * ServiceLoader-based discovery constructor (the production path).
     */
    public RegistryInteropService(GameRegistries registries) {
        this(registries, java.util.ServiceLoader.load(ContentProvider.class)
                .stream().map(java.util.ServiceLoader.Provider::get).toList());
    }

    /**
     * Runs one interop pass: each provider contributes into a fresh
     * validated sink; accepted entries land in the runtime registries;
     * duplicate ids count as rejections (idempotent passes stay clean).
     */
    public ContributionReport runPass() {
        List<String> providerIds = new ArrayList<>();
        List<RegistrySchema.Entry> accepted = new ArrayList<>();
        List<String> rejected = new ArrayList<>();
        List<String> providerFailures = new ArrayList<>();
        for (ContentProvider provider : providers) {
            try {
                CollectingSink sink = new CollectingSink();
                provider.contribute(sink);
                providerIds.add(provider.id());
                for (RegistrySchema.Entry entry : sink.accepted) {
                    if (landInto(entry)) {
                        accepted.add(entry);
                    } else {
                        rejected.add(provider.id() + "/" + entry.id().combined()
                                + ": duplicate id in runtime registries");
                    }
                }
                rejected.addAll(sink.rejections.stream()
                        .map(r -> provider.id() + "/" + r.id().combined()
                                + ": " + r.reason())
                        .toList());
            } catch (Throwable contained) {
                providerFailures.add(provider.id() + ": " + contained);
            }
        }
        return new ContributionReport(providerIds, accepted, rejected,
                providerFailures);
    }

    private boolean landInto(RegistrySchema.Entry entry) {
        ResourceKey id = entry.id();
        Map<String, String> props = entry.properties();
        try {
            switch (entry.kind()) {
                case ITEM -> registries.items().register(id, new ItemContent(id,
                        props.containsKey("maxStack")
                                ? Integer.parseInt(props.get("maxStack")) : 64));
                case BLOCK -> registries.blocks().register(id, new BlockContent(id,
                        parseFloat(props.get("hardness"), 1.0f),
                        parseFloat(props.get("resistance"), 1.0f),
                        parseInt(props.get("luminance"), 0)));
                case ENTITY -> registries.entities().register(id,
                        new EntityContent(id,
                                props.getOrDefault("factoryClass", ""),
                                Boolean.parseBoolean(
                                        props.getOrDefault("clientTracked", "false"))));
            }
            return true;
        } catch (IllegalArgumentException duplicateOrInvalid) {
            return false;
        }
    }

    private static float parseFloat(String raw, float fallback) {
        return raw == null ? fallback : Float.parseFloat(raw);
    }

    private static int parseInt(String raw, int fallback) {
        return raw == null ? fallback : Integer.parseInt(raw);
    }

    /** Validating sink capturing accepted entries and per-entry rejections. */
    private static final class CollectingSink implements RegistrySchemaSink {
        private final List<RegistrySchema.Entry> accepted = new ArrayList<>();
        private final List<RegistrySchema.Rejection> rejections = new ArrayList<>();
        private final Map<String, Boolean> seen = new LinkedHashMap<>();

        @Override
        public boolean contribute(RegistrySchema.Kind kind, ResourceKey id,
                Map<String, String> properties) {
            try {
                RegistrySchema.Entry entry =
                        new RegistrySchema.Entry(kind, id, properties);
                String dedupe = kind + "/" + id.combined();
                if (seen.putIfAbsent(dedupe, Boolean.TRUE) != null) {
                    rejections.add(new RegistrySchema.Rejection(id,
                            "duplicate within provider contribution"));
                    return false;
                }
                accepted.add(entry);
                return true;
            } catch (IllegalArgumentException invalid) {
                rejections.add(new RegistrySchema.Rejection(id,
                        invalid.getMessage()));
                return false;
            }
        }
    }
}
