package dev.itsdropper.vantaloader.mixin;

import java.net.URL;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Classpath view of mod containers visible to Mixin without merging mod class loaders. */
public final class MixinClassPath {
    private static final CopyOnWriteArrayList<ClassLoader> LOADERS = new CopyOnWriteArrayList<>();
    private static final CopyOnWriteArrayList<URL> CONTAINERS = new CopyOnWriteArrayList<>();

    private MixinClassPath() { }

    public static void register(ClassLoader loader, Path jar) {
        if (loader != null) LOADERS.addIfAbsent(loader);
        if (jar != null) {
            try {
                URL url = jar.toAbsolutePath().normalize().toUri().toURL();
                CONTAINERS.addIfAbsent(url);
            } catch (java.net.MalformedURLException exception) {
                throw new IllegalArgumentException("Invalid mod container path: " + jar, exception);
            }
        }
    }

    static List<ClassLoader> loaders() { return List.copyOf(LOADERS); }
    static List<URL> containers() { return List.copyOf(CONTAINERS); }

    static void clear() {
        LOADERS.clear();
        CONTAINERS.clear();
    }
}
