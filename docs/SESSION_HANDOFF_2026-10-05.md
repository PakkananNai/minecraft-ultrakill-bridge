# Session Handoff — 2026-10-05

## Where we stopped

Milestones 5–7 are complete and pushed to GitHub.

- Branch: `m5/shared-memory-framebuffer`
- Remote branch is already pushed.
- Latest pushed commit: `2b348a9` — `docs: update README for milestone 7`
- Branch is 5 commits ahead of `main`, 0 behind.
- A PR was attempted through the GitHub integration but was rejected with HTTP 403; no PR was created by the integration. The web Compare page can be used to create it manually.
- M7 runtime evidence is in `docs/milestone7_report.md`.

## Next milestone

M8 — camera synchronization.

The work for M8 was started but is **NOT complete and NOT committed/pushed**.

### M8 changes currently in the working tree

Host:
- `host/MinecraftBridge/CameraBridge.cs` — new, untracked.
- `host/MinecraftBridge/MinecraftBridgePlugin.cs` — subscribes to camera-state messages and applies them.
- `shared/protocol/csharp/TcpControlChannel.cs` — parses `CameraState` and raises `CameraStateReceived`.

Guest:
- `guest/MinecraftFabricMod/src/main/java/net/pakkanannai/mcfabricmod/GuestControlClient.java` — added camera-state send helper.
- `guest/MinecraftFabricMod/src/main/java/net/pakkanannai/mcfabricmod/MinecraftBridgeMod.java` — sends camera state each client tick.

Protocol:
- Existing `CameraStateMessage` already exists in the shared Java protocol and contains:
  position XYZ, yaw, pitch, roll, FOV, sequence number.
- Existing C# protocol already recognizes `MessageType.CameraState`; only the TCP endpoint dispatch/parser was added in this session.

### Important: M8 validation has NOT passed yet

Do not commit or mark M8 complete yet.

Before continuing:
1. Inspect/verify the M8 code and protocol direction.
2. Build host.
3. Build/test guest.
4. Run cross-language protocol tests.
5. Run `git diff --check`.
6. Add focused camera validation/test coverage if needed.
7. Use the real combined Wayland runtime to verify camera packets and host application.
8. Run Ponytail review before milestone completion.
9. Update `docs/roadmap.md`, `docs/protocol.md`, M8 report, and README only after the gate passes.
10. Commit and push M8 after validation.

## Current working-tree state

The following are intentional M8 edits and must be preserved:
- `GuestControlClient.java`
- `MinecraftBridgeMod.java`
- `MinecraftBridgePlugin.cs`
- `TcpControlChannel.cs`
- `CameraBridge.cs`

There are also many pre-existing untracked artifacts/POCs. Do NOT mass-delete or commit them unless explicitly reviewed:
- `README.md.save`
- `docs/milestone3_architecture_analysis.md`
- `dotnet-install.sh`
- `java_sources.txt`
- `job_111220786687_logs.zip`
- `malformed_consumer`
- `malformed_producer`
- `run_logs.zip`
- `tools/atomic_ipc_validation/`
- `tools/ipc_control_validation/`
- `tools/native_bridge_reachability_poc/`
- `tools/ring_buffer_validation/`
- `tools/robust_mutex_interop_validation/`
- `tools/shared_framebuffer_validation/`
- `tools/tcp_control_validation/`
- `tools/tcp_protocol_validation/`
- `tools/varhandle_interlocked_poc/`

These existed before the current M8 work and were intentionally left untouched.

## Environment facts useful for next session

- Ubuntu 26.04 LTS x86_64
- Kernel 7.0.0-34-generic
- Wayland
- ThinkPad T480, Intel i5-7300U / Intel HD 620 / 32 GB RAM
- Wine 11.0
- ULTRAKILL Unity 2022.3.29f1
- BepInEx 6.0.0-be.788 Unity Mono x64
- Minecraft 1.21.1 Fabric
- Java 21 guest
- Correct ULTRAKILL GUI launch environment used for M7:
  `XDG_RUNTIME_DIR=/run/user/1000 DISPLAY=:0 WAYLAND_DISPLAY=wayland-0 DBUS_SESSION_BUS_ADDRESS=unix:path=/run/user/1000/bus WINEDLLOVERRIDES="winhttp=n,b" wine ./ULTRAKILL.exe`
- Guest launch:
  `cd ~/Documents/antigravity/minecraft-ultrakill-bridge/guest/MinecraftFabricMod && ./gradlew runClient --no-daemon`
- M7 successful runtime session: `2733717925`
- `/dev/uinput` is available and was used for M7 input injection.
- Yarn mappings confirm Minecraft 1.21.1 has `getCameraPosVec(float)` and camera FOV-related mappings.

## Last action

User asked to stop and save progress so the next session can resume without repeating setup.

No M8 commit or push was made.
