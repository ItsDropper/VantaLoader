package dev.itsdropper.vantaloader.runtime;

import dev.itsdropper.vantaloader.api.MinecraftCapabilities;

import java.util.ArrayList;
import java.util.List;

final class RuntimeEvents {
    static final List<String> EVENTS = new ArrayList<>();
    static boolean failShutdown;
    static String failInitializeMod;
    static MinecraftCapabilities lastCapabilities;
    private RuntimeEvents() { }
}
