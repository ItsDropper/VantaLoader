package dev.itsdropper.vantaloader.mixin;

import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.Mixins;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.logging.Logger;

/** Discovers per-mod Mixin configurations and boots the shared Mixin transformer once. */
public final class MixinSupport {
    public static final String BUILTIN_CLIENT_CONFIG = "vantaloader.client.mixins.json";
    private static final Logger LOGGER = Logger.getLogger("VantaLoader.Mixin");
    private static boolean initialized;
    private static final Set<String> REGISTERED_CONFIGS = new HashSet<>();

    private MixinSupport() { }

    public static synchronized void initialize(List<? extends ClassLoader> modLoaders,
                                               List<Path> modJars) throws IOException {
        if (modLoaders.size() != modJars.size()) {
            throw new IllegalArgumentException("Each mod class loader must have exactly one JAR");
        }
        for (int i = 0; i < modLoaders.size(); i++) {
            MixinClassPath.register(modLoaders.get(i), modJars.get(i));
        }

        List<ConfigSource> configs = discoverConfigs(modJars);
        if (!initialized) {
            MixinBootstrap.init();
            MixinEnvironment.getDefaultEnvironment().setSide(MixinEnvironment.Side.CLIENT);
            MixinEnvironment.getCurrentEnvironment().setSide(MixinEnvironment.Side.CLIENT);
            initialized = true;
        }
        boolean builtInClientScreen = "1.21.11".equals(
                System.getProperty("vanta.minecraft.version", "unknown"));
        if (builtInClientScreen && REGISTERED_CONFIGS.add(BUILTIN_CLIENT_CONFIG)) {
            Mixins.addConfiguration(BUILTIN_CLIENT_CONFIG);
        }

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

    private static List<ConfigSource> discoverConfigs(List<Path> modJars) throws IOException {
        List<ConfigSource> result = new ArrayList<>();
        for (Path jar : modJars) {
            try (JarFile archive = new JarFile(jar.toFile())) {
                archive.stream()
                        .filter(entry -> !entry.isDirectory() && entry.getName().endsWith(".mixins.json"))
                        .map(entry -> new ConfigSource(entry.getName(), jar.getFileName().toString()))
                        .forEach(result::add);
            }
        }
        result.sort(Comparator.comparing(ConfigSource::name).thenComparing(ConfigSource::modId));
        return List.copyOf(result);
    }

    private record ConfigSource(String name, String modId) { }
}
