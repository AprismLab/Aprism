package net.minecraft.client;

import java.util.ArrayList;
import java.util.List;

/**
 * Test-sourceset stub of MC Options holding the final keyMappings array
 * (v26.7-Alpha.3). NOT shipped in production artifacts.
 *
 * <!-- GitHub@NDBlockConnect | BlockConnect@StarsailsClover -->
 */
public class Options {

    public final KeyMapping[] keyMappings;

    public Options(KeyMapping[] initial) {
        this.keyMappings = initial;
    }

    /** @return live view of the current array contents (after any swap). */
    public List<KeyMapping> snapshot() {
        List<KeyMapping> out = new ArrayList<>();
        for (KeyMapping k : keyMappings) {
            out.add(k);
        }
        return out;
    }
}
