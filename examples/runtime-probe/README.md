# Runtime integration probe

This CI-only native mod verifies runtime contracts against the real Minecraft 1.21.11 client:

- The adapter delivers client tick START and END events through the public mod event API.
- The probe transforms `net.minecraft.client.player.LocalPlayer` after Auto Sprint, injecting a callback into the mapped `aiStep()V` method.
- The CI smoke test creates a fresh singleplayer world and requires that callback to execute, proving both transformations were accepted and the player ticked in-world.

The smoke artifact includes the in-world screenshot and runtime log.
