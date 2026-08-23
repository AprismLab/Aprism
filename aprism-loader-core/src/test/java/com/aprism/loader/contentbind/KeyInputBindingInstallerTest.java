package com.aprism.loader.contentbind;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.aprism.api.keybinding.KeyBindingSpec;
import com.aprism.loader.contentbind.KeyInputBindingInstaller.BindResult;
import com.aprism.loader.keybinding.KeyBindingRegistryImpl;
// GitHub@NDBlockConnect | BlockConnect@StarsailsClover

/**
 * Tests for {@link KeyInputBindingInstaller} against test-sourceset client
 * stubs (live-game proof follows the smoke path).
 */
class KeyInputBindingInstallerTest {

    @AfterEach
    void clearClient() {
        net.minecraft.client.Minecraft.INSTANCE = null;
    }

    private KeyBindingRegistryImpl registryWithSpec() {
        KeyBindingRegistryImpl reg = new KeyBindingRegistryImpl();
        reg.openWindow();
        reg.register(new KeyBindingSpec("aprism.test.open", "aprism", 75));
        reg.freezeWindow();
        return reg;
    }

    @Test
    void bindsIntoLiveOptionsArray() {
        net.minecraft.client.Minecraft.INSTANCE = new net.minecraft.client.Minecraft();
        KeyInputBindingInstaller installer =
                new KeyInputBindingInstaller(registryWithSpec());
        List<BindResult> results = installer.bindAll();

        assertEquals(1, results.size());
        assertTrue(results.get(0).ok(), "refusal=" + results.get(0).refusal());
        var mappings = net.minecraft.client.Minecraft
                .INSTANCE.options.snapshot();
        assertEquals(1, mappings.size());
        assertEquals("aprism.test.open", mappings.get(0).name);
        assertEquals(75, mappings.get(0).key);
    }

    @Test
    void noClientRefusesFailClosed() {
        List<BindResult> results =
                new KeyInputBindingInstaller(registryWithSpec()).bindAll();
        assertEquals("NO_CLIENT", results.get(0).refusal());
    }

    @Test
    void remapProfileRefusesEverything() {
        KeyInputBindingInstaller installer =
                new KeyInputBindingInstaller(registryWithSpec());
        installer.setRemapProfile(true);
        assertEquals("PROFILE_UNSUPPORTED",
                installer.bindAll().get(0).refusal());
    }
}
