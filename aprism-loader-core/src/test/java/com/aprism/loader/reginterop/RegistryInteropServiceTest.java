package com.aprism.loader.reginterop;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
// GitHub@NDBlockConnect | BlockConnect@StarsailsClover

/**
 * Registry interop tests (v26.9-Alpha.4): schema validation fail-closed
 * semantics, per-entry rejection isolation, ServiceLoader discovery,
 * landing into GameRegistries, and freeze-phase classification.
 *
 * @author BlockConnect@StarsailsClover
 */
class RegistryInteropServiceTest {

    /** Two good entries and two invalid ones; isolation is the point. */
    private static final class MixedProvider implements ContentProvider {
        @Override
        public String id() {
            return "mixed";
        }

        @Override
        public void contribute(RegistrySchemaSink sink) {
            sink.contribute(RegistrySchema.Kind.ITEM,
                    com.aprism.api.registry.ResourceKey.parse("aprism:good_item"),
                    Map.of("maxStack", "8"));
            // Parses as a ResourceKey but violates the schema allow-list.
            sink.contribute(RegistrySchema.Kind.ITEM,
                    com.aprism.api.registry.ResourceKey.parse("aprism:bad_item"),
                    Map.of("mystery", "1"));
            sink.contribute(RegistrySchema.Kind.BLOCK,
                    com.aprism.api.registry.ResourceKey.parse("aprism:good_block"),
                    Map.of("hardness", "3.0", "resistance", "6.0", "luminance", "7"));
            sink.contribute(RegistrySchema.Kind.BLOCK,
                    com.aprism.api.registry.ResourceKey.parse("aprism:bad_block"),
                    Map.of("luminance", "99"));
        }
    }

    /** ServiceLoader-discoverable provider (registered in test resources). */
    public static final class LoadedProvider implements ContentProvider {
        @Override
        public String id() {
            return "loaded";
        }

        @Override
        public void contribute(RegistrySchemaSink sink) {
            sink.contribute(RegistrySchema.Kind.ENTITY,
                    com.aprism.api.registry.ResourceKey.parse("aprism:probe"),
                    Map.of("factoryClass", "com.example.Probe",
                            "clientTracked", "true"));
        }
    }

    @Test
    void schemaValidationIsFailClosedPerEntry() {
        var registries = new com.aprism.loader.registry.GameRegistries();
        RegistryInteropService service = new RegistryInteropService(registries,
                List.of(new MixedProvider()));
        RegistryInteropService.ContributionReport report = service.runPass();

        assertEquals(List.of("mixed"), report.providers());
        assertEquals(2, report.accepted().size());
        assertEquals(2, report.rejected().size());
        assertTrue(report.rejected().get(0).contains("unknown property"));
        assertTrue(report.rejected().get(1).contains("luminance"));
        assertEquals(0, report.providerFailures().size());
        assertTrue(registries.items().keys().stream()
                .anyMatch(k -> k.combined().equals("aprism:good_item")));
        assertTrue(registries.blocks().keys().stream()
                .anyMatch(k -> k.combined().equals("aprism:good_block")));
        assertEquals(8, registries.items().get(
                com.aprism.api.registry.ResourceKey.parse("aprism:good_item"))
                .orElseThrow().maxStack());
        assertTrue(report.toJson().contains("\"accepted\":2"));
    }

    @Test
    void duplicateIdIsRejectedNotThrown() {
        var registries = new com.aprism.loader.registry.GameRegistries();
        ContentProvider duplicate = new ContentProvider() {
            @Override
            public String id() {
                return "dup";
            }

            @Override
            public void contribute(RegistrySchemaSink sink) {
                sink.contribute(RegistrySchema.Kind.ITEM,
                        com.aprism.api.registry.ResourceKey.parse("aprism:dupe"),
                        Map.of());
                sink.contribute(RegistrySchema.Kind.ITEM,
                        com.aprism.api.registry.ResourceKey.parse("aprism:dupe"),
                        Map.of());
            }
        };
        RegistryInteropService service = new RegistryInteropService(registries,
                List.of(duplicate));
        RegistryInteropService.ContributionReport report = service.runPass();
        assertEquals(1, report.accepted().size());
        assertEquals(1, report.rejected().size());
        assertTrue(report.rejected().get(0).contains("duplicate"));
    }

    @Test
    void providerFailureIsContained() {
        var registries = new com.aprism.loader.registry.GameRegistries();
        ContentProvider broken = new ContentProvider() {
            @Override
            public String id() {
                return "broken";
            }

            @Override
            public void contribute(RegistrySchemaSink sink) {
                throw new IllegalStateException("provider exploded");
            }
        };
        RegistryInteropService service = new RegistryInteropService(registries,
                List.of(broken));
        RegistryInteropService.ContributionReport report = service.runPass();
        assertEquals(1, report.providerFailures().size());
        assertTrue(report.providerFailures().get(0).startsWith("broken:"));
    }

    @Test
    void serviceLoaderDiscoversRegisteredProvider() {
        var registries = new com.aprism.loader.registry.GameRegistries();
        RegistryInteropService service = new RegistryInteropService(registries);
        RegistryInteropService.ContributionReport report = service.runPass();
        assertTrue(report.providers().contains("loaded"),
                "ServiceLoader discovery failed: " + report.providers());
        assertTrue(registries.entities().keys().stream()
                .anyMatch(k -> k.combined().equals("aprism:probe")));
    }

    @Test
    void freezeClassificationMatchesVanillaSignatures() {
        assertEquals(FreezeDiagnostics.FreezePhase.POST_FREEZE,
                FreezeDiagnostics.classify(new IllegalStateException(
                        "Registry is already frozen")).phase());
        assertEquals(FreezeDiagnostics.FreezePhase.PRE_BOOTSTRAP,
                FreezeDiagnostics.classify(new IllegalStateException(
                        "Not bootstrapped")).phase());
        // Cause chains classify too.
        IllegalStateException wrapped = new IllegalStateException("wrapper",
                new RuntimeException("Cannot register while frozen"));
        assertEquals(FreezeDiagnostics.FreezePhase.POST_FREEZE,
                FreezeDiagnostics.classify(wrapped).phase());
        assertEquals(FreezeDiagnostics.FreezePhase.UNKNOWN,
                FreezeDiagnostics.classify(new RuntimeException("??")).phase());
        assertTrue(FreezeDiagnostics.success().bindWindowOpen());
        assertTrue(FreezeDiagnostics.success().toJson()
                .contains("\"bindWindowOpen\":true"));
    }
}
