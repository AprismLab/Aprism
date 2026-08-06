package com.aprism.loader;

import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Knot-style classloader that maintains a shared class space for all mods.
 * Minecraft classes are routed through the {@link AprismClassTransformer},
 * while mod classes are loaded from the shared mod jar set.
 *
 * @author BlockConnect@StarsailsClover
 */
public final class AprismClassLoader extends URLClassLoader {

    private final Set<Path> modJars = new LinkedHashSet<>();
    private final AprismClassTransformer transformer;

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
     * Determines whether a class belongs to Minecraft and should be routed
     * through the transformer.
     *
     * @param name the binary class name
     * @return whether the class is a Minecraft class
     */
    private boolean isMinecraftClass(String name) {
        return name.startsWith("net.minecraft.") || name.startsWith("com.mojang.");
    }

    /**
     * Loads a Minecraft class. In this skeleton the bytes are not directly
     * available here; the transformer is wired through the agent, so this
     * delegates to the parent classloader.
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
     * @return the defined package
     */
    public Package definePackage(String name) {
        Package existing = getDefinedPackage(name);
        if (existing != null) {
            return existing;
        }
        return super.definePackage(name, "Aprism", "1", "Aprism", "Aprism", "1", "Aprism", null);
    }
}
