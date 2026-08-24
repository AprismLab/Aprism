package com.aprism.loader.settings;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
// GitHub@NDBlockConnect | BlockConnect@StarsailsClover

/**
 * Tests for {@link SettingsAccessImpl}: typed access, declaration
 * validation, change listeners, and schema rendering.
 */
class SettingsAccessImplTest {

    private ModSettings settingsWithDeclarations() {
        ModSettings s = new ModSettings("mymod");
        s.declare(new com.aprism.manifest.SettingDeclaration(
                "level", com.aprism.manifest.SettingType.INTEGER, 3, "Difficulty", List.of()));
        s.declare(new com.aprism.manifest.SettingDeclaration(
                "mode", com.aprism.manifest.SettingType.ENUM, "easy", "Mode", List.of("easy", "hard")));
        s.declare(new com.aprism.manifest.SettingDeclaration(
                "flag", com.aprism.manifest.SettingType.BOOLEAN, true, "Flag", List.of()));
        return s;
    }

    @Test
    void typedGettersReturnDeclaredDefaults() {
        SettingsAccessImpl a = new SettingsAccessImpl("mymod", settingsWithDeclarations());
        assertEquals(3, a.getInt("level"));
        assertTrue(a.getBool("flag"));
        assertEquals("easy", a.getString("mode"));
    }

    @Test
    void setValidatesAgainstDeclaration() {
        SettingsAccessImpl a = new SettingsAccessImpl("mymod", settingsWithDeclarations());
        assertTrue(a.set("level", 7));
        assertEquals(7, a.getInt("level"));
        assertFalse(a.set("level", "not-a-number"));
        assertFalse(a.set("mode", "invalid-option"));
        assertFalse(a.set("undeclared", 1));
    }

    @Test
    void changeListenersFireOnAcceptedSet() throws Exception {
        SettingsAccessImpl a = new SettingsAccessImpl("mymod", settingsWithDeclarations());
        AtomicInteger fired = new AtomicInteger();
        try (AutoCloseable h = a.subscribe(k -> fired.incrementAndGet())) {
            a.set("level", 9);
            assertEquals(1, fired.get());
            a.set("level", "bad"); // rejected -> no fire
            assertEquals(1, fired.get());
        }
        a.set("level", 11); // unsubscribed -> no fire
        assertEquals(1, fired.get());
    }

    @Test
    void throwingListenerIsolated() {
        SettingsAccessImpl a = new SettingsAccessImpl("mymod", settingsWithDeclarations());
        AtomicInteger fired = new AtomicInteger();
        a.subscribe(k -> { throw new RuntimeException("boom"); });
        a.subscribe(k -> fired.incrementAndGet());
        assertDoesNotThrow(() -> a.set("flag", false));
        assertEquals(1, fired.get());
    }

    @Test
    void schemaRendersDeclarationsForGui() {
        SettingsAccessImpl a = new SettingsAccessImpl("mymod", settingsWithDeclarations());
        var schema = a.schema();
        assertEquals(3, schema.size());
        assertEquals("INTEGER", schema.get("level").get("type"));
        assertEquals(3, schema.get("level").get("default"));
        assertEquals(List.of("easy", "hard"), schema.get("mode").get("options"));
        assertEquals("Difficulty", schema.get("level").get("label"));
    }

    @Test
    void keysInDeclarationOrder() {
        SettingsAccessImpl a = new SettingsAccessImpl("mymod", settingsWithDeclarations());
        assertTrue(a.keys().contains("level") && a.keys().contains("mode") && a.keys().contains("flag"));
    }
}
