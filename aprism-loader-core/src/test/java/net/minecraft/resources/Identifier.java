package net.minecraft.resources;

/**
 * Test-sourceset stub mirroring the MC 26.x Identifier surface used by the
 * content binder (v26.7-Alpha.1). NOT shipped in production artifacts.
 */
public final class Identifier {

    private final String namespace;
    private final String path;

    public Identifier(String namespace, String path) {
        this.namespace = namespace;
        this.path = path;
    }

    public static Identifier parse(String combined) {
        int sep = combined.indexOf(':');
        if (sep < 0) {
            return new Identifier("minecraft", combined);
        }
        return new Identifier(combined.substring(0, sep), combined.substring(sep + 1));
    }

    public String getNamespace() {
        return namespace;
    }

    public String getPath() {
        return path;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Identifier other)) {
            return false;
        }
        return namespace.equals(other.namespace) && path.equals(other.path);
    }

    @Override
    public int hashCode() {
        return 31 * namespace.hashCode() + path.hashCode();
    }

    @Override
    public String toString() {
        return namespace + ":" + path;
    }
}
