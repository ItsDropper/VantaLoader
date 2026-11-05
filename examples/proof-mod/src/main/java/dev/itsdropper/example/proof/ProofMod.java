package dev.itsdropper.example.proof;

import dev.itsdropper.vantaloader.api.ModContext;
import dev.itsdropper.vantaloader.api.VantaMod;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

/**
 * End-to-end smoke-test mod for the VantaLoader Minecraft Java agent.
 * Uses only the native entrypoint API; it does not claim gameplay hooks.
 */
public final class ProofMod implements VantaMod {
    public ProofMod() { }

    @Override
    public void onInitialize(ModContext context) throws Exception {
        Path proofFile = context.configDirectory().resolve("loaded.txt");
        Files.createDirectories(proofFile.getParent());

        String proof = """
                VantaLoader Proof Mod initialized successfully.
                modId=%s
                modVersion=%s
                loaderVersion=%s
                minecraftVersion=%s
                environment=%s
                initializedAt=%s
                """.formatted(
                context.modId(),
                context.modVersion(),
                context.loaderVersion(),
                context.minecraftVersion(),
                context.environment(),
                Instant.now()
        );

        Files.writeString(proofFile, proof, StandardCharsets.UTF_8);

        context.logger().info("========================================");
        context.logger().info("VANTALOADER PROOF MOD INITIALIZED");
        context.logger().info("Minecraft version: " + context.minecraftVersion());
        context.logger().info("Environment: " + context.environment());
        context.logger().info("Proof file written: " + proofFile.toAbsolutePath());
        context.logger().info("========================================");
    }

    @Override
    public void onShutdown() {
        System.getLogger(ProofMod.class.getName())
                .log(System.Logger.Level.INFO, "VantaLoader Proof Mod shut down.");
    }
}
