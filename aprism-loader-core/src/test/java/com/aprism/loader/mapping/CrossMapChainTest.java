package com.aprism.loader.mapping;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
// GitHub@NDBlockConnect | BlockConnect@StarsailsClover

/**
 * Cross-map chain validation tests: intermediary -> official (tiny v2) ->
 * runtime (client.txt) must resolve for every sampled class and fail the
 * chain when a link is broken.
 *
 * @author BlockConnect@StarsailsClover
 */
class CrossMapChainTest {

    @TempDir
    Path tempDir;

    private static final String TINY =
            "tiny\t2\t0\tofficial\tintermediary\n"
            + "c\tnet.minecraft.world.level.block.BlockPos\tnet.minecraft.class_2338\n";

    private static final String CLIENT_TXT =
            "# fixture\n"
            + "net.minecraft.world.level.block.BlockPos -> ji:\n";

    @Test
    void chainResolvesThroughBothLayers() throws IOException {
        Path tiny = tempDir.resolve("fixture.tiny");
        Path client = tempDir.resolve("client.txt");
        Files.writeString(tiny, TINY);
        Files.writeString(client, CLIENT_TXT);

        CrossMapChain.ChainReport report = CrossMapChain.validate(tiny, client, 8);
        assertEquals(1, report.tinyClasses());
        assertEquals(1, report.officialClasses());
        assertEquals(1, report.sampled());
        assertEquals(1, report.resolvable());
        assertTrue(report.valid());
        assertTrue(report.toJson().contains("\"valid\":true"));
    }

    @Test
    void brokenLinkInvalidatesChain() throws IOException {
        // client.txt does not map BlockPos: the official->runtime hop fails.
        Path tiny = tempDir.resolve("fixture.tiny");
        Path client = tempDir.resolve("client.txt");
        Files.writeString(tiny, TINY);
        Files.writeString(client, "# empty fixture\n");

        CrossMapChain.ChainReport report = CrossMapChain.validate(tiny, client, 8);
        assertEquals(0, report.resolvable());
        assertFalse(report.valid());
        assertEquals(1, report.unresolved().size());
    }
}
