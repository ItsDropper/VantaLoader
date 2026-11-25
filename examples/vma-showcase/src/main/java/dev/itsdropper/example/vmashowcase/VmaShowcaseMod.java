package dev.itsdropper.example.vmashowcase;

import dev.itsdropper.vantaloader.api.*;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.concurrent.atomic.AtomicBoolean;

/** Demonstrates ordinary mod behavior using VMA without bytecode transformations. */
public final class VmaShowcaseMod implements VantaMod {
    private static final ConfigKey<Boolean> ENABLED = ConfigKey.bool("enabled", true);
    private final AtomicBoolean tickObserved = new AtomicBoolean();
    private final AtomicBoolean gameStateObserved = new AtomicBoolean();
    private final AtomicBoolean hudRenderObserved = new AtomicBoolean();

    public VmaShowcaseMod() { }

    @Override
    public void onInitialize(ModContext context) {
        ModConfig config = context.config();
        if (!config.contains(ENABLED.id())) {
            config.set(ENABLED, true);
            try {
                config.save();
            } catch (IOException exception) {
                throw new UncheckedIOException("Cannot create VMA showcase config", exception);
            }
        }

        boolean enabled = config.get(ENABLED);
        context.logger().info("VMA showcase config loaded: enabled=" + enabled);
        if (!enabled) return;

        if (context.clientCommands().isAvailable()) {
            context.clientCommands().register(new ClientCommand(
                    "vmashowcase", "Show VMA showcase status", "vmashowcase",
                    command -> {
                        context.logger().info("VMA showcase: client command executed");
                        command.reply("VMA showcase is active for " + context.modId());
                    }));
        }

        context.tasks().execute(() ->
                context.logger().info("VMA showcase: managed background task executed"));

        context.events().subscribe(ClientTickEvent.class, EventPriority.NORMAL, event -> {
            if (event.phase() == ClientTickEvent.Phase.START
                    && tickObserved.compareAndSet(false, true)) {
                context.logger().info("VMA showcase: high-level client tick event delivered");
            }
        });

        if (context.minecraftCapabilities().supports(MinecraftCapability.HUD_RENDERING)) {
            context.events().subscribe(HudRenderEvent.class, EventPriority.NORMAL, event -> {
                HudRenderContext hud = event.context();
                hud.fillRect(4, 4, 104, 22, 0x99000000);
                hud.drawText("Vanta VMA", 9, 9, 0xFFFFFFFF);
                if (hudRenderObserved.compareAndSet(false, true)) {
                    context.logger().info("VMA showcase: HUD render context delivered");
                }
            });
        }

        if (context.minecraftCapabilities().supports(MinecraftCapability.CLIENT_GAME_STATE)) {
            context.events().subscribe(ClientGameTickEvent.class, EventPriority.NORMAL, event -> {
                if (event.phase() != ClientTickEvent.Phase.START || !event.snapshot().inWorld()
                        || !gameStateObserved.compareAndSet(false, true)) return;
                ClientPlayerSnapshot player = event.snapshot().player().orElseThrow();
                ClientWorldSnapshot world = event.snapshot().world().orElseThrow();
                context.logger().info("VMA showcase: player/world snapshot delivered; dimension="
                        + world.dimensionId() + ", position=" + player.x() + "," + player.y() + "," + player.z());
            });
        }
    }

    @Override public void onShutdown() { }
}
