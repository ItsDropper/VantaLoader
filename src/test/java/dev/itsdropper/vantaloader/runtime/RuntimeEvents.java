package dev.itsdropper.vantaloader.runtime;

import dev.itsdropper.vantaloader.api.MinecraftCapabilities;
import dev.itsdropper.vantaloader.api.ModServices;
import dev.itsdropper.vantaloader.api.ServiceKey;
import dev.itsdropper.vantaloader.api.ServiceRegistration;
import dev.itsdropper.vantaloader.api.KeyBindingRegistration;

import java.util.ArrayList;
import java.util.List;

final class RuntimeEvents {
    static final List<String> EVENTS = new ArrayList<>();
    static boolean failShutdown;
    static boolean failFatalShutdown;
    static String failInitializeMod;
    static MinecraftCapabilities lastCapabilities;
    static final java.util.concurrent.atomic.AtomicInteger resourceReloads = new java.util.concurrent.atomic.AtomicInteger();
    static volatile ClassLoader lastReloadClassLoader;
    static final ServiceKey<Runnable> TEST_SERVICE = ServiceKey.of("service_provider:greeter", Runnable.class);
    static volatile ServiceRegistration serviceRegistration;
    static volatile ModServices providerServices;
    static volatile KeyBindingRegistration keyBindingRegistration;
    private RuntimeEvents() { }
}
