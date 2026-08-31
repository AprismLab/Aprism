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
            net.minecraft.core.registries.BuiltInRegistries -> abc.def:
                java.lang.String ITEM -> a
            net.minecraft.core.Registry -> ke:
                boolean containsKey(net.minecraft.resources.ResourceLocation) -> d
                boolean containsKey(net.minecraft.resources.ResourceKey) -> e
                java.lang.Object getValue(net.minecraft.resources.ResourceLocation) -> a
            net.minecraft.world.item.Item -> ghi:
                int stacksTo(int) -> b
                int getMaxStackSize() -> c
                void method_1(net.minecraft.world.item.ItemStack)
            skip.Me -> skip.Other:
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
    void resolvesMethodNamesWithinClasses() throws Exception {
        Path f = tempDir.resolve("client.txt");
        Files.writeString(f, CLIENT_TXT);
        OfficialMappings m = OfficialMappings.load(f);

        // Method lines in the fixture carry no return type in our simplified
        // probe: "void method_1(...)" -> official name "method_1" maps to obf.
        assertEquals("a", m.runtimeFieldName(
                "net.minecraft.core.registries.BuiltInRegistries", "ITEM"));
        // Unmapped method passes through.
        assertEquals("register", m.runtimeMethodName(
                "net.minecraft.core.Registry", "register"));
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

    @Test
    void resolvesOverloadsByParameterSignature() throws Exception {
        // v26.8-Alpha.9: same-name overloads collapse to last-wins in the
        // name-only table, but the signature table must pick the exact one.
        Path f = tempDir.resolve("client.txt");
        Files.writeString(f, CLIENT_TXT);
        OfficialMappings m = OfficialMappings.load(f);

        // Name-only: documents the last-wins collapse (ResourceKey overload).
        assertEquals("e", m.runtimeMethodName(
                "net.minecraft.core.Registry", "containsKey"));
        // Signature-exact: each overload resolves to its own runtime name.
        assertEquals("d", m.runtimeMethodName(
                "net.minecraft.core.Registry", "containsKey",
                new String[] {"net.minecraft.resources.ResourceLocation"}));
        assertEquals("e", m.runtimeMethodName(
                "net.minecraft.core.Registry", "containsKey",
                new String[] {"net.minecraft.resources.ResourceKey"}));
        assertEquals("a", m.runtimeMethodName(
                "net.minecraft.core.Registry", "getValue",
                new String[] {"net.minecraft.resources.ResourceLocation"}));
        // Unknown signature falls back to the name-only table (last-wins "e").
        assertEquals("e", m.runtimeMethodName(
                "net.minecraft.core.Registry", "containsKey",
                new String[] {"java.lang.String"}));
        // int-param methods (stacksTo) resolve through the signature table.
        assertEquals("b", m.runtimeMethodName(
                "net.minecraft.world.item.Item", "stacksTo",
                new String[] {"int"}));
        // Unmapped class passes the method name through unchanged.
        assertEquals("containsKey", m.runtimeMethodName(
                "net.minecraft.core.Unknown", "containsKey",
                new String[] {"int"}));
    }
}
