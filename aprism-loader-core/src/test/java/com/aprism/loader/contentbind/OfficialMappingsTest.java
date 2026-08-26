package com.aprism.loader.contentbind;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
// GitHub@NDBlockConnect | BlockConnect@StarsailsClover

/**
 * Tests for {@link OfficialMappings}: ProGuard client.txt parsing and
 * official-to-runtime class/field-name resolution (DEC-PRE261 Option A
 * foundation + member fields, v26.8-Alpha.6).
 */
class OfficialMappingsTest {

    @TempDir
    Path tempDir;

    private static final String CLIENT_TXT = """
            # comment line should be skipped
            abc.def -> net.minecraft.core.registries.BuiltInRegistries:
                java.lang.String ITEM -> a
            ghi -> net.minecraft.world.item.Item:
                void method_1(net.minecraft.world.item.ItemStack)
            skip.Me -> skip.Me:
            """;

    @Test
    void loadsClassEntriesAndSkipsMembersAndComments() throws Exception {
        Path f = tempDir.resolve("client.txt");
        Files.writeString(f, CLIENT_TXT);
        OfficialMappings m = OfficialMappings.load(f);

        assertNotNull(m);
        assertTrue(m.size() >= 3);
        assertEquals("abc.def",
                m.runtimeName("net.minecraft.core.registries.BuiltInRegistries"));
        assertEquals("ghi", m.runtimeName("net.minecraft.world.item.Item"));
    }

    @Test
    void unknownNamesPassThroughUnchanged() throws Exception {
        Path f = tempDir.resolve("client.txt");
        Files.writeString(f, CLIENT_TXT);
        OfficialMappings m = OfficialMappings.load(f);

        assertEquals("com.mojang.brigadier.CommandDispatcher",
                m.runtimeName("com.mojang.brigadier.CommandDispatcher"));
    }

    @Test
    void absentFileLoadsAsNull() throws Exception {
        assertNull(OfficialMappings.load(tempDir.resolve("nope.txt")));
        assertNull(OfficialMappings.load(null));
    }

    @Test
    void resolvesStaticFieldNamesWithinClasses() throws Exception {
        Path f = tempDir.resolve("client.txt");
        Files.writeString(f, CLIENT_TXT);
        OfficialMappings m = OfficialMappings.load(f);

        assertEquals("a", m.runtimeFieldName(
                "net.minecraft.core.registries.BuiltInRegistries", "ITEM"));
        // Unmapped class/field passes through unchanged.
        assertEquals("ITEM", m.runtimeFieldName(
                "net.minecraft.world.item.Item", "ITEM"));
    }

    @Test
    void remapGateStillRefusesWithoutMappings() {
        // Without official mappings, REMAPPED profiles keep refusing.
        var reg = new com.aprism.loader.registry.GameRegistries();
        var k = com.aprism.api.registry.ResourceKey.parse("aprism:x");
        reg.items().register(k,
                new com.aprism.api.registry.ItemContent(k, 4));
        GameContentBindingInstaller installer = new GameContentBindingInstaller(reg);
        installer.setRemapProfile(true);
        var results = installer.bindAll();
        assertEquals("PROFILE_UNSUPPORTED", results.get(0).refusal());
    }
}
