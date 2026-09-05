package com.aprism.conformance.probe;

import java.util.List;
import java.util.Map;

import com.aprism.conformance.CoverageMatrix;
import com.aprism.conformance.Probe;
import com.aprism.conformance.ProbeResult;
import com.aprism.loader.registry.GameRegistries;
import com.aprism.loader.reginterop.ContentProvider;
import com.aprism.loader.reginterop.FreezeDiagnostics;
import com.aprism.loader.reginterop.RegistryInteropService;
import com.aprism.loader.reginterop.RegistrySchema;
import com.aprism.loader.reginterop.RegistrySchemaSink;

/**
 * Registry interop probe (v26.9-Alpha.4): the provider SPI, schema
 * validation (fail-closed per entry), landing into the runtime registries,
 * and freeze-phase classification must all work in one pass.
 *
 * @author BlockConnect@StarsailsClover
 */
public final class RegistryInteropProbe implements Probe {
    //GitHub@NDBlockConnect | BlockConnect@StarsailsClover

    @Override
    public ProbeResult run() {
        CoverageMatrix.Cell cell = new CoverageMatrix.Cell("registry",
                "interop schema + providers + freeze diagnostics", "unit",
                CoverageMatrix.Status.CONTRACT_ONLY,
                "executed by ConformanceKit on every run");
        try {
            GameRegistries registries = new GameRegistries();
            ContentProvider provider = new ContentProvider() {
                @Override
                public String id() {
                    return "conformance";
                }

                @Override
                public void contribute(RegistrySchemaSink sink) {
                    sink.contribute(RegistrySchema.Kind.ITEM,
                            com.aprism.api.registry.ResourceKey.parse(
                                    "aprism:interop_probe_item"),
                            Map.of("maxStack", "4"));
                    // Valid key, schema-invalid property: must be rejected
                    // per-entry without touching the accepted one.
                    sink.contribute(RegistrySchema.Kind.ITEM,
                            com.aprism.api.registry.ResourceKey.parse(
                                    "aprism:interop_probe_bad"),
                            Map.of("mystery", "1"));
                }
            };
            RegistryInteropService service =
                    new RegistryInteropService(registries, List.of(provider));
            RegistryInteropService.ContributionReport report = service.runPass();
            boolean landed = registries.items().keys().stream()
                    .anyMatch(k -> k.combined().equals("aprism:interop_probe_item"));
            boolean isolated = report.accepted().size() == 1
                    && report.rejected().size() == 1;
            FreezeDiagnostics.PhaseReport phase =
                    FreezeDiagnostics.classify(new IllegalStateException(
                            "Registry is already frozen"));
            boolean classified = phase.phase()
                    == FreezeDiagnostics.FreezePhase.POST_FREEZE;
            boolean pass = landed && isolated && classified;
            return new ProbeResult(cell, pass, report.toJson()
                    + " freeze=" + phase.toJson());
        } catch (Throwable t) {
            return new ProbeResult(cell, false, t.toString());
        }
    }
}
