package com.aprism.loader.contentbind;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.aprism.api.registry.BlockContent;
import com.aprism.api.registry.ItemContent;
import com.aprism.api.registry.ResourceKey;
import com.aprism.loader.registry.GameRegistries;
import com.aprism.loader.contentbind.GameContentBindingInstaller.BindingResult;

/**
 * Tests for {@link GameContentBindingInstaller} against the test-sourceset
 * MC stubs (the live-game proof is the smoke harness).
 */
class GameContentBindingInstallerTest {

    private GameRegistries registries;

    @BeforeEach
    void setUp() {
        registries = new GameRegistries();
    }

    @Test
    void emptyRegistriesBindNothing() {
        GameContentBindingInstaller installer = new GameContentBindingInstaller(registries);
        List<BindingResult> results = installer.bindAll();
        assertTrue(results.isEmpty());
    }

    @Test
    void itemBindsIntoLiveRegistry() {
        registries.items().register(ResourceKey.parse("aprism:ruby"),
                new ItemContent(ResourceKey.parse("aprism:ruby"), 16));
        GameContentBindingInstaller installer = new GameContentBindingInstaller(registries);
        List<BindingResult> results = installer.bindAll();

        assertEquals(1, results.size());
        assertTrue(results.get(0).ok(), "expected ok, got " + results.get(0).refusal());
        assertNotNull(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(idFor("ruby")));
    }

    @Test
    void blockBindsIntoLiveRegistry() {
        ResourceKey key = ResourceKey.parse("aprism:glowsoil");
        registries.blocks().register(key,
                new BlockContent(key, 1.0f, 1.0f, 15));
        GameContentBindingInstaller installer = new GameContentBindingInstaller(registries);
        List<BindingResult> results = installer.bindAll();

        assertEquals(1, results.size());
        assertTrue(results.get(0).ok(), "expected ok, got " + results.get(0).refusal());
        assertNotNull(net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(idFor("glowsoil")));
    }

    @Test
    void remapProfileRefusesEverythingWhenNoOfficialMappings() {
        registries.items().register(ResourceKey.parse("aprism:x"),
                new ItemContent(ResourceKey.parse("aprism:x"), 1));
        GameContentBindingInstaller installer = new GameContentBindingInstaller(registries);
        installer.setRemapProfile(true);

        List<BindingResult> results = installer.bindAll();
        assertEquals(1, results.size());
        assertFalse(results.get(0).ok());
        assertEquals("PROFILE_UNSUPPORTED", results.get(0).refusal());
    }

    @Test
    void remapProfileWithOfficialMappingsNoLongerGatesOut() throws Exception {
        // DEC-PRE261 Option A: when both remapProfile and official mappings are
        // supplied, the binder proceeds to attempt the cross-mapped binding
        // path. The gate's PROFILE_UNSUPPORTED message must no longer appear.
        registries.items().register(ResourceKey.parse("aprism:x"),
                new ItemContent(ResourceKey.parse("aprism:x"), 1));
        Path stubTxt = java.nio.file.Files.createTempFile("aprism-stub-", "-client.txt");
        try {
            java.nio.file.Files.writeString(stubTxt, "");
            OfficialMappings stub = OfficialMappings.load(stubTxt);
            GameContentBindingInstaller installer = new GameContentBindingInstaller(registries);
            installer.setRemapProfile(true);
            installer.setOfficialMappings(stub);
            List<BindingResult> results = installer.bindAll();
            assertEquals(1, results.size());
            // The gate opens; the failure (if any) now reflects the runtime
            // side, which in this stub harness is simply no binding target
            // found. What we assert: the gate message is no longer returned.
            if (!results.get(0).ok()) {
                assertNotEquals("PROFILE_UNSUPPORTED", results.get(0).refusal());
            }
        } finally {
            java.nio.file.Files.deleteIfExists(stubTxt);
        }
    }

    private static net.minecraft.resources.Identifier idFor(String path) {
        return net.minecraft.resources.Identifier.parse("aprism:" + path);
    }
}
