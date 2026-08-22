package com.aprism.loader.contentbind;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.aprism.api.commands.CommandRegistration;
import com.aprism.api.commands.CommandSpec;
import com.aprism.loader.contentbind.CommandBindingInstaller.BindResult;
// GitHub@NDBlockConnect | BlockConnect@StarsailsClover

/**
 * Tests for {@link CommandBindingInstaller} against the test-sourceset
 * Brigadier stubs (live-game dispatcher discovery is a follow-up milestone).
 */
class CommandBindingInstallerTest {

    @Test
    void bindsFrozenSpecsIntoLiveDispatcher() {
        var reg = new com.aprism.loader.commands.CommandRegistrationImpl();
        reg.openWindow();
        boolean[] ran = {false};
        reg.register(new CommandSpec("aprism_ping", "test", (Runnable) () -> ran[0] = true));
        reg.freezeWindow();

        CommandBindingInstaller installer = new CommandBindingInstaller(reg);
        installer.attachDispatcher(
                new com.mojang.brigadier.CommandDispatcher<Object>());
        List<BindResult> results = installer.bindAll();

        assertEquals(1, results.size());
        assertTrue(results.get(0).ok(), "refusal=" + results.get(0).refusal());
    }

    @Test
    void noDispatcherRefusesFailClosed() {
        var reg = new com.aprism.loader.commands.CommandRegistrationImpl();
        reg.openWindow();
        reg.register(new CommandSpec("aprism_x", "test", (Runnable) () -> { }));
        reg.freezeWindow();

        List<BindResult> results = new CommandBindingInstaller(reg).bindAll();
        assertEquals(1, results.size());
        assertFalse(results.get(0).ok());
        assertEquals("NO_DISPATCHER", results.get(0).refusal());
    }

    @Test
    void remapProfileRefusesEverything() {
        var reg = new com.aprism.loader.commands.CommandRegistrationImpl();
        reg.openWindow();
        reg.register(new CommandSpec("aprism_y", "test", (Runnable) () -> { }));
        reg.freezeWindow();

        CommandBindingInstaller installer = new CommandBindingInstaller(reg);
        installer.setRemapProfile(true);
        List<BindResult> results = installer.bindAll();
        assertEquals("PROFILE_UNSUPPORTED", results.get(0).refusal());
    }

    @Test
    void emptyRegistrationBindsNothing() {
        var reg = new com.aprism.loader.commands.CommandRegistrationImpl();
        List<BindResult> results =
                new CommandBindingInstaller(reg).bindAll();
        assertTrue(results.isEmpty());
    }
}
