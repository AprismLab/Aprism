package net.minecraft.client;

/**
 * Test-sourceset stub of the MC client singleton (v26.7-Alpha.3). NOT
 * shipped in production artifacts.
 *
 * <!-- GitHub@NDBlockConnect | BlockConnect@StarsailsClover -->
 */
public class Minecraft {

    public static Minecraft INSTANCE;
    public final Options options = new Options(new KeyMapping[0]);

    public static Minecraft getInstance() {
        return INSTANCE;
    }
}
