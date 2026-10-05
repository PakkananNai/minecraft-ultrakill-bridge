# Milestone 8 — Camera Synchronization Report

## Status

**PASS**

M8 camera synchronization is implemented and verified through clean host/guest builds, the cross-language camera packet fixture, and a real Wayland ULTRAKILL + Minecraft 1.21.1 runtime.

## Implementation

- Minecraft samples its active camera entity on the client tick.
- Camera packets are sent guest → host over the existing canonical MCUB TCP control plane.
- Guest camera updates are rate-limited to one packet every three client ticks to avoid unnecessary control-plane traffic.
- The host TCP worker validates the canonical payload and queues the newest camera sequence.
- Unity applies the newest camera state only from the Unity main-thread `Update()`.
- Minecraft coordinates map `(x, y, z)` → Unity `(x, y, -z)`.
- Rotation maps Minecraft yaw/pitch/roll → Unity `(180 - yaw, pitch, -roll)`.
- The mirrored Unity camera is disabled, so M8 does not replace ULTRAKILL's active gameplay camera.
- The bridge camera is recreated if Unity destroys its scene object during a scene transition.
- FOV is applied when the guest reports a value in the valid `(1, 179)` degree range.
- Applied-camera logging is rate-limited to one diagnostic line per 30 applied states.

## Automated verification

- `./tools/build_host.sh` — PASS.
- `(cd guest/MinecraftFabricMod && ./gradlew clean build --no-daemon)` — PASS; all guest tests passed.
- `./tools/cross_lang_test/run_cross_test.sh` — PASS; `camerastate.bin` and all existing protocol fixtures matched.
- `git diff --check` — PASS.
- A previous test run while the live runtime still owned TCP port 47653 produced two `BindException` failures in guest socket tests. The runtime was stopped and the same guest test suite was rerun; the clean rerun passed. This was an environmental port collision, not a test/code failure.

## Real runtime verification

Final runtime used:

- Ubuntu 26.04 LTS / Wayland.
- ULTRAKILL Unity 2022.3.29f1 through Wine 11.0.
- BepInEx 6.0.0-be.788 host plugin.
- Minecraft 1.21.1 Fabric / Java 21.
- Live loopback MCUB TCP session `1435401860`.
- Guest shared framebuffer identity was validated for that session.
- Minecraft singleplayer world `New World` was loaded and the player entered the overworld.

Host evidence:

- `M8_CAMERA_BRIDGE_CREATED authoritative=guest coordinateMap=(x,y,z)->(x,y,-z)`
- `MCUB_SESSION_ESTABLISHED id=1435401860 client=MinecraftFabricMod version=1.21.1`
- `M8_CAMERA_APPLIED count=30 seq=340 unityPos=(12.50, 73.62, 53.50) unityEuler=(0.00, 180.00, 0.00) fov=70.00`
- `M8_CAMERA_APPLIED count=60 seq=378 unityPos=(12.50, 73.62, 53.50) unityEuler=(0.00, 180.00, 0.00) fov=70.00`
- `M8_CAMERA_APPLIED count=90 seq=420 unityPos=(12.50, 73.62, 53.50) unityEuler=(0.00, 180.00, 0.00) fov=70.00`
- No `M8_CAMERA_APPLY_FAILED` was observed during the final successful runtime after the scene-lifetime fix.

Guest evidence:

- `Connected to bridge at 127.0.0.1:47653 (session 1435401860)`
- `Shared framebuffer identity validated (session 1435401860, path ...)`
- The Minecraft server reported the local player entering the overworld at `(12.5, 72.0, -53.5)`.
- The host-applied camera position `(12.50, 73.62, 53.50)` matches the documented Z-axis inversion and the camera entity's eye-height offset.

## Failure and recovery during M8

The first live implementation exposed a Unity scene-lifetime bug: the temporary bridge `GameObject` was destroyed during scene transitions, producing `NullReferenceException` during camera application. The host was instrumented, the failure was reproduced, and the bridge was changed to use `DontDestroyOnLoad` plus defensive camera-object recreation. A subsequent runtime produced continuous `M8_CAMERA_APPLIED` records with no apply failures.

A second issue was excessive camera packet/log frequency. The guest initially sent a camera state every client tick and the host logged every application. The final implementation reduces camera traffic to one packet every three client ticks and rate-limits diagnostic logging.

## Protocol compatibility

`CAMERA_STATE` remains the existing canonical message type (`Type = 9`) and payload. No new header format, message ID, or alternate TCP protocol was introduced.

Direction:

`Minecraft camera entity → GuestControlClient → MCUB TCP → TcpControlServer → CameraBridge → Unity mirrored camera`

## Ponytail review

M8 remains small and focused: one host camera bridge, one guest sampling hook, and one existing control-plane dispatch path. No new dependency or speculative abstraction was introduced.

**Review result:** Lean already. Ship.

## Completion gate

M8 is complete. The implementation builds, protocol compatibility passes, the real Minecraft camera state crosses the TCP boundary, Unity applies the transformed state on its main thread, and the scene-lifetime failure found during validation has been fixed and re-tested.
