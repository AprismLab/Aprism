package com.aprism.manifest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * JUnit 5 tests for {@link ManifestParser} and {@link AprismManifest#fromJson}.
 *
 * @author BlockConnect@StarsailsClover
 */
class ManifestParserTest {

    private static final String SAMPLE_MANIFEST = """
            {
              "schemaVersion": 1,
              "id": "examplemod",
              "version": "1.0.0",
              "displayName": "Example Mod",
              "description": "An example Aprism mod",
              "environment": "*",
              "entrypoints": {
                "main": ["com.example.ExampleMod"]
              },
              "mixins": ["examplemod.mixins.json"],
              "depends": {
                "aprism": "26.0-Alpha.1"
              },
              "platforms": {},
              "accessWidener": null,
              "provides": [],
              "custom": {}
            }
            """;

    @Test
    void parsesSampleManifestFields() {
        AprismManifest manifest = AprismManifest.fromJson(SAMPLE_MANIFEST);

        assertNotNull(manifest);
        assertEquals(1, manifest.schemaVersion());
        assertEquals("examplemod", manifest.id());
        assertEquals("1.0.0", manifest.version());
        assertEquals("Example Mod", manifest.displayName());
        assertEquals("An example Aprism mod", manifest.description());
        assertEquals("*", manifest.environment());
        assertNotNull(manifest.entrypoints());
        assertEquals(1, manifest.entrypoints().size());
        assertEquals("com.example.ExampleMod", manifest.entrypoints().get("main").get(0));
        assertTrue(manifest.mixins().contains("examplemod.mixins.json"));
        assertEquals("26.0-Alpha.1", manifest.depends().get("aprism"));
    }
}
