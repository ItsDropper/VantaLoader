package dev.itsdropper.vantaloader.mixin;

import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.Mixins;

import java.io.IOException;
import java.nio.file.Path;
import dev.itsdropper.vantaloader.discovery.ModArtifact;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.logging.Logger;

/** Discovers per-mod Mixin configurations and boots the shared Mixin transformer once. */
public final class MixinSupport {
    private static final Logger LOGGER = Logger.getLogger("VantaLoader.Mixin");
    private static boolean initialized;
    private static final Set<String> REGISTERED_CONFIGS = new HashSet<>();

    private MixinSupport() { }

    public static synchronized void initialize(List<? extends ClassLoader> modLoaders,
                                               List<ModArtifact> mods) throws IOException {
        if (modLoaders.size() != mods.size()) {
            throw new IllegalArgumentException("Each mod class loader must have exactly one mod artifact");
        }
        for (int i = 0; i < modLoaders.size(); i++) {
            MixinClassPath.register(modLoaders.get(i), mods.get(i).path());
        }

        List<ConfigSource> configs = discoverConfigs(mods);
        if (!initialized) {
            System.setProperty("mixin.side", "CLIENT");
            MixinBootstrap.init();
            MixinEnvironment.getDefaultEnvironment().setSide(MixinEnvironment.Side.CLIENT);
            MixinEnvironment.getCurrentEnvironment().setSide(MixinEnvironment.Side.CLIENT);
            initialized = true;
        }
        // VantaLoader's own Minecraft UI hooks use the exact-version native adapter.
        // Mixin remains available for third-party mods and their declared configurations.

        Set<String> names = new HashSet<>(REGISTERED_CONFIGS);
        for (ConfigSource config : configs) {
            if (!names.add(config.name())) {
                throw new IOException("Duplicate Mixin configuration resource: " + config.name()
                        + " (also declared by " + config.modId() + ")");
            }
            try {
                Mixins.addConfiguration(config.name());
                REGISTERED_CONFIGS.add(config.name());
                LOGGER.info("Registered Mixin config " + config.name() + " from " + config.modId());
            } catch (Throwable failure) {
                if (failure instanceof VirtualMachineError fatal) throw fatal;
                if (failure instanceof ThreadDeath death) throw death;
                throw new IOException("Cannot register Mixin config " + config.name()
                        + " from " + config.modId(), failure);
            }
        }
        if (!VantaMixinService.isInitialized()) {
            throw new IllegalStateException("Mixin bootstrap completed without creating its transformer");
        }
        LOGGER.info("Sponge Mixin active; discovered " + configs.size() + " mod configuration(s)");
    }

    static List<String> discoverConfigNames(List<ModArtifact> mods) throws IOException {
        return discoverConfigs(mods).stream().map(ConfigSource::name).toList();
    }

    private static List<ConfigSource> discoverConfigs(List<ModArtifact> mods) throws IOException {
        java.util.Map<String, String> discovered = new java.util.LinkedHashMap<>();
        for (ModArtifact mod : mods) {
            String modId = mod.descriptor().id();
            try (JarFile archive = new JarFile(mod.path().toFile())) {
                for (String declared : mod.descriptor().mixins()) {
                    validateConfigPath(declared, modId);
                    var entry = archive.getJarEntry(declared);
                    if (entry == null || entry.isDirectory()) {
                        throw new IOException("Mixin config '" + declared + "' declared by "
                                + modId + " is missing from its mod JAR");
                    }
                    addConfig(discovered, declared, modId);
                }
                archive.stream()
                        .filter(entry -> !entry.isDirectory() && entry.getName().endsWith(".mixins.json"))
                        .forEach(entry -> {
                            try {
                                validateConfigPath(entry.getName(), modId);
                                addConfig(discovered, entry.getName(), modId);
                            } catch (IOException exception) {
                                throw new ConfigDiscoveryException(exception);
                            }
                        });
            } catch (ConfigDiscoveryException exception) {
                throw exception.failure;
            }
        }
        return discovered.entrySet().stream()
                .map(entry -> new ConfigSource(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(ConfigSource::name).thenComparing(ConfigSource::modId))
                .toList();
    }

    private static void validateConfigPath(String path, String modId) throws IOException {
        if (path == null || path.isBlank() || path.startsWith("/") || path.indexOf('\\') >= 0
                || path.matches("^[A-Za-z]:.*")) {
            throw new IOException("Mixin config path declared by " + modId + " is not a safe JAR resource path");
        }
        for (String segment : path.split("/", -1)) {
            if (segment.isBlank() || segment.equals(".") || segment.equals("..")) {
                throw new IOException("Mixin config path declared by " + modId + " is not a safe JAR resource path");
            }
        }
        if (!path.endsWith(".mixins.json")) {
            throw new IOException("Mixin config declared by " + modId + " must end in .mixins.json: " + path);
        }
    }

    private static void addConfig(java.util.Map<String, String> discovered, String path, String modId)
            throws IOException {
        String previous = discovered.putIfAbsent(path, modId);
        if (previous != null && !previous.equals(modId)) {
            throw new IOException("Mixin config '" + path + "' is declared by both " + previous + " and " + modId);
        }
    }

    private static final class ConfigDiscoveryException extends RuntimeException {
        private final IOException failure;
        private ConfigDiscoveryException(IOException failure) { this.failure = failure; }
    }

    private record ConfigSource(String name, String modId) { }
}
