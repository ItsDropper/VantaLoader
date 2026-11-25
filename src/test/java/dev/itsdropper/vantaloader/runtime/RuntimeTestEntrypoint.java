package dev.itsdropper.vantaloader.runtime;

import dev.itsdropper.vantaloader.api.ModContext;
import dev.itsdropper.vantaloader.api.VantaMod;
import dev.itsdropper.vantaloader.api.KeyBinding;
import dev.itsdropper.vantaloader.api.ClientCommand;

public final class RuntimeTestEntrypoint implements VantaMod {
    public RuntimeTestEntrypoint() { }

    @Override
    public void onInitialize(ModContext context) {
        RuntimeEvents.EVENTS.add("init:" + context.modId());
        RuntimeEvents.lastCapabilities = context.minecraftCapabilities();
        if (context.modId().equals("keybinding_fixture")) {
            RuntimeEvents.keyBindingRegistration = context.keyBindings().register(
                    new KeyBinding("test", "Test binding", 65, 0,
                            action -> RuntimeEvents.EVENTS.add("key:" + action.name())));
        }
        if (context.modId().equals("command_fixture")) {
            RuntimeEvents.clientCommandRegistration = context.clientCommands().register(
                    new ClientCommand("vma-test", "VMA command test", "vma-test <text>",
                            command -> RuntimeEvents.EVENTS.add("command:" + String.join(" ", command.arguments()))));
        }
        if (context.modId().equals("service_provider")) {
            RuntimeEvents.providerServices = context.services();
            RuntimeEvents.serviceRegistration = context.services().register(
                    RuntimeEvents.TEST_SERVICE, () -> RuntimeEvents.EVENTS.add("service:called"));
        }
        if (context.modId().equals("service_consumer")) {
            context.services().require(RuntimeEvents.TEST_SERVICE).run();
        }
        if (context.modId().equals("resource_fixture")) {
            context.resources().register("runtime-test",
                    context.configDirectory().resolve("resources"), reload -> {
                        return new dev.itsdropper.vantaloader.api.ResourceReloadPlan() {
                            @Override public void commit() {
                                RuntimeEvents.lastReloadClassLoader = Thread.currentThread().getContextClassLoader();
                                RuntimeEvents.resourceReloads.incrementAndGet();
                            }
                            @Override public void rollback() { RuntimeEvents.resourceReloads.decrementAndGet(); }
                        };
                    });
        }
        if (context.modId().equals(RuntimeEvents.failInitializeMod)) {
            RuntimeEvents.failInitializeMod = null;
            throw new IllegalStateException("simulated initialization failure");
        }
    }

    @Override
    public void onShutdown() {
        RuntimeEvents.EVENTS.add("stop");
        if (RuntimeEvents.failFatalShutdown) {
            RuntimeEvents.failFatalShutdown = false;
            throw new OutOfMemoryError("simulated fatal shutdown failure");
        }
        if (RuntimeEvents.failShutdown) {
            RuntimeEvents.failShutdown = false;
            throw new AssertionError("simulated shutdown failure");
        }
    }
}
