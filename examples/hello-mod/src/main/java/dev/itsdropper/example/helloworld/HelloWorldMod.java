package dev.itsdropper.example.helloworld;

import dev.itsdropper.vantaloader.api.ModContext;
import dev.itsdropper.vantaloader.api.VantaMod;

/** Minimal native VantaLoader mod demonstrating the entrypoint lifecycle. */
public final class HelloWorldMod implements VantaMod {
    public HelloWorldMod() { }

    @Override
    public void onInitialize(ModContext context) {
        context.logger().info("Hello from VantaLoader! Mod id: " + context.modId());
        context.logger().info("Configuration directory: " + context.configDirectory());
    }

    @Override
    public void onShutdown() {
        System.getLogger(HelloWorldMod.class.getName())
                .log(System.Logger.Level.INFO, "Hello World mod stopped.");
    }
}
