# Runtime integration probe

This CI-only native mod verifies two runtime contracts against the real Minecraft 1.21.11 client:

- The adapter delivers client tick START and END events through the public mod event API.
- A second mod can transform the same `net.minecraft.client.Minecraft` class after the built-in adapter, preserving the first transformation while adding its own marker method.

The CI smoke test requires both tick-phase log markers and successful game startup with the composed client class.
