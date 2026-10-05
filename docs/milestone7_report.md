# Milestone 7 — Input Synchronization Report

## Status

**PASS**

M7 input synchronization is implemented and verified through automated protocol tests plus a real Wayland runtime session using the actual ULTRAKILL BepInEx host and Minecraft Fabric guest.

## Implementation

- Host `InputBridge` uses Unity Input System keyboard/mouse state.
- F8 toggles guest focus and captures/restores the host's enabled input actions.
- Keyboard press/release, relative mouse movement, mouse buttons, and wheel are encoded as canonical `INPUT_EVENT` packets.
- `INPUT_FOCUS` is sent on focus changes and requests held-input release when focus is withdrawn.
- Guest TCP input is queued off-thread and applied on the Minecraft render/client thread.
- Guest focus loss releases tracked keyboard and mouse-button state.
- M7 runtime logging records the first applied event of each input type without per-frame log spam.

## Automated verification

- `git diff --check` — PASS.
- `./tools/build_host.sh` — PASS.
- `(cd guest/MinecraftFabricMod && ./gradlew clean build --no-daemon)` — PASS; 10 actionable tasks completed.
- `./tools/cross_lang_test/run_cross_test.sh` — PASS; `inputevent.bin` and `inputfocus.bin` plus all existing protocol fixtures matched.
- Guest unit tests — PASS in the final clean build.

## Real runtime verification

The final verified GUI run used the actual Wayland session with:

- ULTRAKILL Unity/BepInEx host.
- Minecraft 1.21.1 Fabric guest.
- Loopback TCP control session `2733717925`.
- Shared framebuffer identity validated by the guest.

Host evidence from `BepInEx/LogOutput.log`:

- `M7_INPUT_BACKEND active=Unity.InputSystem`
- `M7_INPUT_FOCUS focus=True releaseHeldKeys=True`
- `M7_INPUT_EVENT_SENT type=3 ... dx=-14 dy=3` — relative mouse movement.
- `M7_INPUT_EVENT_SENT type=1 key=87` — W key down.
- `M7_INPUT_EVENT_SENT type=2 key=87` — W key up.
- `M7_INPUT_EVENT_SENT type=4 key=0` — left mouse down.
- `M7_INPUT_EVENT_SENT type=5 key=0` — left mouse up.
- `M7_INPUT_EVENT_SENT type=6 ... wheel=1` — mouse wheel.
- Repeated F8 injection produced verified host focus transitions `true → false → true → false → true`, demonstrating reversible focus switching and cleanup.

Guest evidence from `run/logs/latest.log`:

- `M7_GUEST_INPUT_FOCUS focus=true ... thread=Render thread`.
- `M7_GUEST_INPUT_APPLIED type=3 ... thread=Render thread`.
- `M7_GUEST_INPUT_APPLIED type=1 key=87 ... thread=Render thread`.
- `M7_GUEST_INPUT_APPLIED type=2 key=87 ... thread=Render thread`.
- `M7_GUEST_INPUT_APPLIED type=4 key=0 ... thread=Render thread`.
- `M7_GUEST_INPUT_APPLIED type=5 key=0 ... thread=Render thread`.

The guest application log directly verifies that mouse movement, keyboard down/up, and mouse-button down/up crossed the TCP boundary and were applied on the Minecraft render thread. Wheel serialization and host transmission were additionally verified in the same live session and by the cross-language fixture suite.

## Review

Ponytail review found no safe over-engineering reduction remaining in the M7 implementation. The implementation stays split into the host input bridge and guest main-thread application path, with no new dependency introduced.

## Known non-blocking runtime note

An earlier launch attempt without the GUI environment (`DISPLAY`/`WAYLAND_DISPLAY`) failed during GLFW initialization. This was an environment-launch error, not an M7 implementation failure. The final runtime was relaunched with the actual Wayland session environment and passed the M7 input gate.

## Completion gate

M7 implementation, build, automated protocol compatibility, real host input capture, focus switching, guest input application, and cleanup behavior have sufficient evidence for PASS.
