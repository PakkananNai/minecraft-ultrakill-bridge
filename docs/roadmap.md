# Project Implementation Roadmap

This roadmap tracks the development progress of the **Minecraft × ULTRAKILL Bridge**, strictly adhering to the milestones defined in `prompt.md`.

---

## Progress Overview

| Milestone | Title | Status | Verification Criteria |
| :--- | :--- | :--- | :--- |
| **0** | **Environment Inspection** | ✅ **Done** | Hardware, Wine, BepInEx, Unity assemblies, .NET/Java toolchains audited. |
| **1** | **Minimal Host Plugin** | ✅ **Done** | BepInEx 6 plugin compiled, loaded in ULTRAKILL, logged engine/GPU diagnostics. |
| **2** | **Shared Protocol** | ✅ **Done** | Versioned packet format, serialization, and unit tests passed (C# & Java). |
| **3** | **Interprocess Communication** | ✅ **Implemented** | File-backed mapping lifecycle plus canonical 16-byte TCP control, handshake/session negotiation, ping/pong, shutdown, cross-language and socket validation. No ring buffer claim. |
| **4** | **Minecraft Guest Mod** | ✅ **Done** | Fabric 1.21.1 client mod connects to control channel, completes handshake, and validates advertised shared mapping identity. |
| **5** | **Framebuffer Capture** | ✅ **Complete** | Canonical triple-buffer, START_STREAM identity, asynchronous BGRA8 PBO capture, cross-process stress, and live Minecraft 1.21.1 validation passed. |
| **6** | **Host Rendering** | 🚧 **In Progress / BLOCKED** | Unity Texture2D + full-screen RawImage consumer is implemented and host build/runtime surface creation is verified; combined Minecraft→ULTRAKILL frame presentation is not yet verified because the current ULTRAKILL/Wine run terminates before a stable TCP session remains available. |
| **7** | **Input Integration** | 📋 Planned | Keyboard/mouse forwarding with F8 focus switcher and stuck-key prevention. |
| **8** | **Camera Synchronization** | 📋 Planned | Coordinate system mapping and camera transform synchronization. |
| **9** | **Block Interaction & Collision**| 📋 Planned | Block raycasts, placement, and breaking. |
| **10** | **Entity Synchronization** | 📋 Planned | Lightweight entity metadata discovery and positioning. |
| **11** | **Damage & Gameplay** | 📋 Planned | Bi-directional damage events and health synchronization. |
| **12** | **Optimization & Stability** | 📋 Planned | Frame pacing, latency profiling, memory leak auditing, hardening. |

---

## Detailed Milestone Checklist

### Milestone 0 — Environment Inspection ✅
- [x] Inspect BepInEx 6.0.0-be.788 installation and assemblies.
- [x] Verify Unity engine version (2022.3.29f1) and target framework (`netstandard2.1`).
- [x] Audit C# compilers (`mcs.exe` in Wine Mono verified; .NET SDK status documented).
- [x] Audit Java runtimes (Found Microsoft OpenJDK 21.0.7 LTS & OpenJDK 25 in Prism Launcher).
- [x] Document Wine environment, filesystem path mapping, and graphics driver (D3D11 on Intel HD 620).
- [x] Create `AGENTS.md`, `README.md`, `docs/environment.md`, `docs/architecture.md`, `docs/roadmap.md`.

### Milestone 1 — Minimal Host Plugin ✅
- [x] Create host plugin project structure under `host/MinecraftBridge/`.
- [x] Implement `MinecraftBridgePlugin` inheriting from `BepInEx.Unity.Mono.BaseUnityPlugin`.
- [x] Log BepInEx initialization, Unity engine version, runtime platform, and system specs.
- [x] Implement graceful `OnDestroy` lifecycle handler.
- [x] Implement automated build script (`tools/build_host.sh`) invoking verified Wine Mono compiler.
- [x] Deploy compiled DLL to `BepInEx/plugins/MinecraftBridge.dll` in ULTRAKILL directory.
- [x] Execute test run under Wine and verify plugin load and log output in `BepInEx/LogOutput.log`.
  * Verified in `BepInEx/LogOutput.log`: `Loading [MinecraftBridge 0.1.0]`, Unity 2022.3.29f1, Intel HD Graphics 620 (Direct3D11), 30691 MB RAM.

### Milestone 3 — Interprocess Communication ✅
- [x] Add production C# framing over TCP streams using the canonical shared 16-byte MCUB header codec.
- [x] Host plugin owns a loopback TCP listener on port 47653; accepted connections negotiate HELLO/HELLO_ACK and have bounded I/O timeouts and deterministic close paths.
- [x] Implement canonical PING/PONG echo and one-way SHUTDOWN handling without adding message IDs or changing payloads.
- [x] Add create/open/size-checked file-backed mapping utility without defining framebuffer slots or ownership.
- [x] Validate native ↔ Wine Mono TCP in both directions; fragmented and coalesced frames; malformed/oversized/truncated frames; disconnect and cleanup.
- [x] Validate native ↔ managed mapping values/layout and both create/open paths.
- [x] Preserve C# and Java protocol unit and cross-language packet round-trip suites.
- [x] Guest Fabric runtime integration is tracked under Milestone 4; triple-buffer ownership and data-plane recovery remain Milestone 5 work.

Evidence logs: `tools/tcp_protocol_validation/logs/20261004T114700Z-425212/` (`KEEP_LOGS=1`). Host plugin compiled with `tools/build_host.sh`. C# protocol tests passed 52 assertions; Java protocol tests passed 51 assertions.

### Milestone 5 — Framebuffer Capture ✅ Complete
- [x] Define and implement the canonical 128-byte mapping/slot headers and three-slot BGRA8 layout.
- [x] Implement Java guest and C# host shared-buffer primitives, generation checks, atomic ownership, checksums, and latest-frame selection.
- [x] Advertise mapping identity through existing `START_STREAM` ID 6; guest validates session, size, header, and generation before use.
- [x] Implement Fabric post-HUD framebuffer capture using a three-entry OpenGL PBO ring and zero-timeout GPU fence polling.
- [x] Publish completed BGRA8 readbacks from a dedicated worker thread; drop frames when no slot/PBO is available.
- [x] Add direct-ByteBuffer publication, metadata ownership, reserved-byte, cancellation, identity, and protocol tests.
- [x] Add guest reconnect with bounded exponential backoff and validate fresh session/mapping identity after a simulated host disconnect.
- [x] Run 2,000 cross-process frames in each Java↔C# direction with metadata, checksum, payload, and latest-frame verification.
- [x] Run in the actual Minecraft 1.21.1 client and verify live BGRA8 framebuffer publication at 854x480 and 1366x700 runtime resolutions.
- [x] Validate simulated host disconnect/reconnect with fresh session IDs, generations, mappings, and continued live frame publication.

M5 runtime evidence: 267 live frames were observed at 1366x700 with final sequence 267, 3,824,800-byte payloads, stride 5,464, BGRA8 format, and non-zero checksums. A prior 854x480 run produced 33+ live frames. The reconnect run produced 9 frames on each of two fresh mappings. Unity host presentation is now the active M6 work; full-resolution 3840x2160 GPU throughput is not claimed because the development display is not 4K.

### Milestone 6 — Host Rendering 🚧 In Progress / BLOCKED
- [x] Add C# latest-frame consumer helper that acquires, validates, reads, and releases one READY slot.
- [x] Create a Unity `Texture2D` using `TextureFormat.BGRA32` with no mipmaps and update it with `LoadRawTextureData` + `Apply(false, false)`.
- [x] Create a full-screen `ScreenSpaceOverlay` `RawImage` presentation surface with a high sorting order and raycast disabled.
- [x] Add resize handling by recreating the texture when framebuffer dimensions change.
- [x] Build the host plugin against the actual Unity UI/UIModule assemblies and deploy it to the installed ULTRAKILL BepInEx environment.
- [x] Verify ULTRAKILL loads the updated plugin and logs `M6_RENDER_SURFACE_CREATED` under Wine with the Intel HD 620 D3D11 backend.
- [ ] Verify a live Minecraft 1.21.1 connection remains established to the running ULTRAKILL host and produces `M6_FRAME_PRESENTED` logs.
- [ ] Visually verify the Minecraft frame is displayed inside the ULTRAKILL render output.

**Current M6 blocker:** The host surface itself initializes successfully, but the current combined Wine ULTRAKILL runtime terminates before the Minecraft guest can maintain the control session. The observed Minecraft side subsequently reports `Connection refused`. The failure is not currently attributed to the new texture upload path because no `M6_FRAME_PRESENTED` call was reached. Further investigation must isolate the ULTRAKILL runtime termination before M6 can be marked PASS.
