package com.aprism.loader.status;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for {@link StatusPublisher}.
 */
class StatusPublisherTest {

    @TempDir
    Path tempDir;

    @Test
    void publishesStatusFileToGameRoot() {
        Map<String, Object> snapshot = StatusPublisher.buildSnapshot(
                "v26.6-Alpha.2", "JE", "26.2", "LOADED", null, null);
        Path published = StatusPublisher.publish(tempDir, snapshot);

        assertNotNull(published);
        assertEquals(StatusPublisher.FILE_NAME, published.getFileName().toString());
        assertTrue(Files.exists(published));
    }

    @Test
    void snapshotCarriesSchemaAndIdentity() throws IOException {
        Map<String, Object> snapshot = StatusPublisher.buildSnapshot(
                "v26.6-Alpha.2", "JE", "26.2", "LOADED", null, null);

        assertEquals(StatusPublisher.SCHEMA_VERSION, snapshot.get("schemaVersion"));
        assertEquals("v26.6-Alpha.2", snapshot.get("aprismVersion"));
        assertEquals("JE", snapshot.get("mcEdit"));
        assertEquals("26.2", snapshot.get("mcVersion"));
        assertEquals("LOADED", snapshot.get("phase"));

        Path published = StatusPublisher.publish(tempDir, snapshot);
        String json = Files.readString(published);
        assertTrue(json.contains("aprism.status/v1"));
        assertTrue(json.contains("v26.6-Alpha.2"));
        assertTrue(json.contains("LOADED"));
    }

    @Test
    void publishWithNullArgsReturnsNull() {
        assertNull(StatusPublisher.publish(null, Map.of()));
        assertNull(StatusPublisher.publish(tempDir, null));
    }

    @Test
    void unpublishRemovesTheFile() {
        Map<String, Object> snapshot = StatusPublisher.buildSnapshot(
                "v26.6", "JE", "26.2", "LOADED", null, null);
        Path published = StatusPublisher.publish(tempDir, snapshot);
        assertNotNull(published);
        assertTrue(Files.exists(published));

        StatusPublisher.unpublish(tempDir);
        assertFalse(Files.exists(published));
    }

    @Test
    void unpublishWithNullRootIsNoop() {
        assertDoesNotThrow(() -> StatusPublisher.unpublish(null));
    }

    @Test
    void republishReplacesPreviousContent() throws IOException {
        Map<String, Object> first = StatusPublisher.buildSnapshot(
                "v26.6", "JE", "26.2", "LOADED", null, null);
        StatusPublisher.publish(tempDir, first);

        Map<String, Object> second = StatusPublisher.buildSnapshot(
                "v26.6", "JE", "26.2", "SHUTDOWN", null, null);
        StatusPublisher.publish(tempDir, second);

        String json = Files.readString(tempDir.resolve(StatusPublisher.FILE_NAME));
        assertFalse(json.contains("\"LOADED\""));
        assertTrue(json.contains("SHUTDOWN"));
    }

    @Test
    void noTmpFileLeftBehindAfterPublish() {
        Map<String, Object> snapshot = StatusPublisher.buildSnapshot(
                "v26.6", "JE", "26.2", "LOADED", null, null);
        StatusPublisher.publish(tempDir, snapshot);

        assertFalse(Files.exists(tempDir.resolve(StatusPublisher.FILE_NAME + ".tmp")));
    }

    @Test
    void snapshotCountsUnitsFromModList() {
        com.aprism.loader.modmenu.ModListRegistry modList =
                new com.aprism.loader.modmenu.ModListRegistry();
        modList.register(com.aprism.loader.modmenu.ModListEntry.of(
                "examplemod", "1.0.0", "Example Mod", "", "mod", "", "examplemod.aje",
                com.aprism.loader.modmenu.ModListState.LOADED));
        modList.register(com.aprism.loader.modmenu.ModListEntry.of(
                "brokenmod", "0.1.0", "Broken Mod", "", "mod", "", "brokenmod.aje",
                com.aprism.loader.modmenu.ModListState.FAILED));

        Map<String, Object> snapshot = StatusPublisher.buildSnapshot(
                "v26.6", "JE", "26.2", "LOADED", modList, null);

        assertEquals(1, snapshot.get("okCount"));
        assertEquals(1, snapshot.get("failureCount"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> units = (List<Map<String, Object>>) snapshot.get("units");
        assertEquals(2, units.size());
    }

    @Test
    void snapshotEnrichesDurationsFromReport() {
        com.aprism.loader.modmenu.ModListRegistry modList =
                new com.aprism.loader.modmenu.ModListRegistry();
        modList.register(com.aprism.loader.modmenu.ModListEntry.of(
                "examplemod", "1.0.0", "Example Mod", "", "mod", "", "examplemod.aje",
                com.aprism.loader.modmenu.ModListState.LOADED));

        com.aprism.loader.LoadReport report = new com.aprism.loader.LoadReport();
        report.recordOk("mod", "examplemod", "1.0.0", 42L);

        Map<String, Object> snapshot = StatusPublisher.buildSnapshot(
                "v26.6", "JE", "26.2", "LOADED", modList, report);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> units = (List<Map<String, Object>>) snapshot.get("units");
        assertEquals(42L, units.get(0).get("durationMs"));
    }

    @Test
    void nullFieldsRenderAsEmptyStrings() throws IOException {
        Map<String, Object> snapshot = StatusPublisher.buildSnapshot(
                null, null, null, null, null, null);
        Path published = StatusPublisher.publish(tempDir, snapshot);
        String json = Files.readString(published);

        // Null identity fields are normalized to "" so the schema stays stable.
        assertFalse(json.contains("null"));
        assertTrue(json.contains("\"aprismVersion\": \"\""));
    }
}
