# Milestone 9 — Block Interaction & Collision Report

**Status: PASS (2026-10-06)**

## Implementation

M9 forwards host mouse input through the canonical MCUB input path and applies it on the Minecraft client thread through Minecraft's normal mouse callbacks.

Path:

`ULTRAKILL Input → MCUB INPUT_EVENT → GuestInputBridge → Mouse.onMouseButton() → Minecraft normal interaction flow`

The implementation uses a small Fabric `@Invoker` mixin for Minecraft's mouse callbacks. No alternate synthetic input mechanism was added.

Additional M9 runtime behavior:
- guest focus closes the Minecraft pause menu when focus is acquired;
- held mouse buttons are released on focus loss when requested;
- host mouse button state transitions are derived from Unity Input System `isPressed`, avoiding missed edge events in the Wine/Unity runtime;
- temporary M9 diagnostic mixins/logging used during bring-up were removed after verification.

## Build

- Host: `./tools/build_host.sh` — **SUCCESS**
- Guest: `./gradlew build -x test` — **BUILD SUCCESSFUL**
- `git diff --check` — **PASS**

## Runtime verification

Real Minecraft 1.21.1/Fabric runtime evidence was collected after deploying the guest JAR.

### Left click / block breaking

Verified runtime sequence:
- forwarded left click reached `doAttack()`;
- `attackBlock()` was reached for a real block target;
- `updateBlockBreakingProgress()` advanced from `0.0666` through `0.9333` and completed with progress reset to `0.0` and cooldown `5`;
- a subsequent target was observed, consistent with the previous target being removed;
- held left click continued the normal breaking progression;
- release stopped the breaking state cleanly in earlier M9 runtime tests.

### Right click / placement or use

Verified runtime sequence:
- forwarded right click reached `doItemUse()`;
- `interactBlock()` was reached on the main hand;
- a real interaction returned `result=SUCCESS accepted=true` at runtime, e.g. block target `(38,73,-27)` and later `(42,73,-27)`.

Other attempts returned `PASS accepted=false`, which is normal when the targeted block/item does not accept the interaction. The important successful path was observed in the actual Minecraft runtime.

### Collision

M9 does not introduce a second physics implementation or fake collision system. Minecraft remains authoritative for its own block collision/world state. The verified break/place interactions therefore use Minecraft's native collision/block rules rather than bypassing them.

A full ULTRAKILL movement-vs-Minecraft collision synchronization model is intentionally not introduced here; that remains a later architectural decision rather than speculative M9 code.

## Review

- Canonical 16-byte MCUB framing remains unchanged.
- No old 24-byte TCP framing was reintroduced.
- Interaction stays on the Minecraft client thread.
- No blocking network work was added to the render/tick interaction path.
- Temporary diagnostic mixins were removed after verification.
- `MinecraftMouseAccessor` is retained because it is the minimal stable bridge into Minecraft's existing mouse callback methods.
- No unrelated untracked artifacts were deleted.

## Known issues

- Guest socket tests can collide with a live runtime using TCP port `47653`; this is test isolation, not a production protocol regression.
- Wayland may log `65548: Wayland: The platform does not support setting the cursor position`; this was observed independently of successful M9 interaction and did not prevent the verified interaction path.
- ULTRAKILL/Minecraft cross-world collision synchronization is not yet implemented; the authoritative model remains an open architectural decision.

## Result

**M9 PASS.** The real runtime input path, block breaking, right-click interaction, held input, and release behavior have sufficient runtime evidence for the current milestone. No M10 implementation is included in this milestone.
