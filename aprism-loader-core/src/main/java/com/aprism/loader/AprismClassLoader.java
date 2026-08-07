package com.aprism.loader;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

import com.aprism.loader.remap.BytecodeRemapper;

/**
 * Knot-style classloader that maintains a shared class space for all mods.
 * Minecraft classes are routed through the parent (they come from the game),
 * while mod classes are loaded from the shared mod jar set.
 *
 * <p>When a {@link BytecodeRemapper} is installed (pre-26.1 profile), every
 * mod class is remapped from Intermediary names to official names at define
 * time, so mod bytecode compiled against Intermediary resolves against the
 * obfuscated game jar. Minecraft 26.1+ (no-remap profile) never installs a
 * remapper and this classloader behaves as a plain shared class space.
 *
 * @author BlockConnect@StarsailsClover
 */
public final class AprismClassLoader extends URLClassLoader {

    private final Set<Path> modJars = new LinkedHashSet<>();
    private final AprismClassTransformer transformer;
    private volatile BytecodeRemapper bytecodeRemapper;

    /**
     * Constructs a new classloader.
     *
     * @param parent      the parent classloader
     * @param transformer the class transformer used for Minecraft classes
     */
    public AprismClassLoader(ClassLoader parent, AprismClassTransformer transformer) {
        super(new URL[0], parent);
        this.transformer = transformer;
    }

    /**
     * Adds a mod jar to the shared class space.
     *
     * @param jar the jar path to add
     */
    public void addModJar(Path jar) {
        modJars.add(jar);
        try {
            addURL(jar.toUri().toURL());
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Invalid jar path: " + jar, e);
        }
    }

    /**
     * Installs the bytecode remapper applied to every mod class at define
     * time. A {@code null} value removes remapping (no-remap profile).
     *
     * @param remapper the remapper, or {@code null}
     */
    public void setBytecodeRemapper(BytecodeRemapper remapper) {
        this.bytecodeRemapper = remapper;
    }

    /**
     * @return the installed bytecode remapper, or {@code null} if none
     */
    public BytecodeRemapper getBytecodeRemapper() {
        return bytecodeRemapper;
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        synchronized (getClassLoadingLock(name)) {
            Class<?> c = findLoadedClass(name);
            if (c == null) {
                if (isMinecraftClass(name)) {
                    c = loadMinecraftClass(name);
                } else {
                    try {
                        c = findClass(name);
                    } catch (ClassNotFoundException ignored) {
                        c = super.loadClass(name, false);
                    }
                }
            }
            if (resolve) {
                resolveClass(c);
            }
            return c;
        }
    }

    /**
     * Finds and defines a mod class. When a bytecode remapper is installed,
     * the class bytes are read from the mod jars, remapped
     * (intermediary → official), and defined with the remapped references.
     *
     * @param name the binary class name
     * @return the defined class
     * @throws ClassNotFoundException if the class is not in any mod jar
     */
    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        BytecodeRemapper remapper = this.bytecodeRemapper;
        if (remapper == null) {
            return super.findClass(name);
        }
        String resourceName = name.replace('.', '/') + ".class";
        URL resource = findResource(resourceName);
        if (resource == null) {
            throw new ClassNotFoundException(name);
        }
        byte[] bytes;
        try (InputStream in = resource.openStream()) {
            bytes = in.readAllBytes();
        } catch (IOException e) {
            throw new ClassNotFoundException("Failed to read class bytes: " + name, e);
        }
        try {
            bytes = remapper.remap(bytes);
        } catch (IOException e) {
            throw new ClassNotFoundException("Failed to remap class: " + name, e);
        }
        return defineClass(name, bytes, 0, bytes.length);
    }

    /**
     * Determines whether a class belongs to Minecraft and should be routed
     * to the parent (the game provides Minecraft classes, never mod jars).
     *
     * @param name the binary class name
     * @return whether the class is a Minecraft class
     */
    private boolean isMinecraftClass(String name) {
        return name.startsWith("net.minecraft.") || name.startsWith("com.mojang.");
    }

    /**
     * Loads a Minecraft class by delegating to the parent classloader.
     *
     * @param name the binary class name
     * @return the loaded class
     * @throws ClassNotFoundException if the class cannot be loaded
     */
    private Class<?> loadMinecraftClass(String name) throws ClassNotFoundException {
        return super.loadClass(name, false);
    }

    /**
     * Defines a package with the given name, returning the existing package if
     * one is already defined.
     *
     * @param name the package name
     * @return the existing or newly defined package
     */
    public Package definePackage(String name) {
        Package existing = getDefinedPackage(name);
        if (existing != null) {
            return existing;
        }
        return super.definePackage(name, "Aprism", "1", "Aprism", "Aprism", "1", "Aprism", null);
    }
}
