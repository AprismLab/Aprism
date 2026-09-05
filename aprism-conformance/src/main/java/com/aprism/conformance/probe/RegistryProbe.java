package com.aprism.conformance.probe;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.aprism.api.registry.ItemContent;
import com.aprism.api.registry.ResourceKey;
import com.aprism.conformance.CoverageMatrix;
import com.aprism.conformance.Probe;
import com.aprism.conformance.ProbeResult;
import com.aprism.loader.contentbind.GameContentBindingInstaller;
import com.aprism.loader.contentbind.OfficialMappings;
import com.aprism.loader.registry.GameRegistries;

/**
 * Registry contract probe (v26.9-Alpha.1): typed content registration,
 * the fail-closed REMAPPED refusal without official mappings, and the gate
 * opening (then failing closed per-entry with TARGET_UNRESOLVED in a
 * non-game JVM) when mappings are supplied. The live-game counterpart is
 * evidenced by the 1.21.4 smoke harness.
 *
 * @author BlockConnect@StarsailsClover
 */
public final class RegistryProbe implements Probe {
    //GitHub@NDBlockConnect | BlockConnect@StarsailsClover

    @Override
    public ProbeResult run() {
        CoverageMatrix.Cell cell = new CoverageMatrix.Cell("registry",
                "typed content + REMAPPED gate", "unit+live-ref",
                CoverageMatrix.Status.VERIFIED_LIVE,
                "live: smoke-harness/1.21.4 (FACT 2026-08-31, 2/2 + readbacks); contract re-run by kit");
        try {
            GameRegistries registries = new GameRegistries();
            ResourceKey key = ResourceKey.parse("aprism:conformance_item");
            registries.items().register(key, new ItemContent(key, 16));
            boolean registered = registries.items().keys().contains(key);

            GameContentBindingInstaller refused =
                    new GameContentBindingInstaller(registries);
            refused.setRemapProfile(true);
            List<GameContentBindingInstaller.BindingResult> refusedResults =
                    refused.bindAll();
            boolean gateClosed = !refusedResults.isEmpty()
                    && "PROFILE_UNSUPPORTED".equals(
                            refusedResults.get(0).refusal());

            Path stubTxt = Files.createTempFile("aprism-conf-", "-client.txt");
            OfficialMappings stub = OfficialMappings.load(stubTxt);
            Files.deleteIfExists(stubTxt);
            GameContentBindingInstaller opened =
                    new GameContentBindingInstaller(registries);
            opened.setRemapProfile(true);
            opened.setOfficialMappings(stub);
            List<GameContentBindingInstaller.BindingResult> openedResults =
                    opened.bindAll();
            boolean gateOpen = !openedResults.isEmpty()
                    && "TARGET_UNRESOLVED".equals(
                            openedResults.get(0).refusal());

            boolean pass = registered && gateClosed && gateOpen;
            return new ProbeResult(cell, pass, "registered=" + registered
                    + " gateClosed=" + gateClosed + " gateOpen=" + gateOpen);
        } catch (Throwable t) {
            return new ProbeResult(cell, false, t.toString());
        }
    }
}
