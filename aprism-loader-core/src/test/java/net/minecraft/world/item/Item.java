package net.minecraft.world.item;

/**
 * Test-sourceset stub of MC Item + Properties (v26.7-Alpha.1). NOT shipped.
 */
public class Item {

    public Item(Properties properties) {
        properties.touch();
    }

    public static class Properties {

        private boolean touched;

        public Properties stacksTo(int max) {
            return this;
        }

        void touch() {
            touched = true;
        }
    }
}
