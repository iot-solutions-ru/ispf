package com.ispf.server.driver.pack;

import java.net.URL;
import java.net.URLClassLoader;

/**
 * Driver packs ship their own Bouncy Castle and Milo. The server classpath also
 * contains Bouncy Castle, including a second {@code bcprov} line, and parent-first
 * loading mixes those classes with the pack. Milo then fails with
 * {@code NoSuchFieldError} on {@code IANAObjectIdentifiers}.
 */
final class DriverPackClassLoader extends URLClassLoader {

    static {
        ClassLoader.registerAsParallelCapable();
    }

    DriverPackClassLoader(URL packJar, ClassLoader parent) {
        super(new URL[] {packJar}, parent);
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        if (!preferPack(name)) {
            return super.loadClass(name, resolve);
        }
        synchronized (getClassLoadingLock(name)) {
            Class<?> loaded = findLoadedClass(name);
            if (loaded == null) {
                try {
                    loaded = findClass(name);
                } catch (ClassNotFoundException notInPack) {
                    loaded = null;
                }
            }
            if (loaded == null) {
                return super.loadClass(name, resolve);
            }
            if (resolve) {
                resolveClass(loaded);
            }
            return loaded;
        }
    }

    static boolean preferPack(String name) {
        return name.startsWith("org.bouncycastle.") || name.startsWith("org.eclipse.milo.");
    }
}
