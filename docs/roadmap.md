# Project Implementation Roadmap

This roadmap tracks the development progress of the **Minecraft × ULTRAKILL Bridge**, strictly adhering to the milestones defined in `prompt.md`.

---

## Progress Overview

| Milestone | Title | Status | Verification Criteria |
| :--- | :--- | :--- | :--- |
| **0** | **Environment Inspection** | ✅ **Done** | Hardware, Wine, BepInEx, Unity assemblies, .NET/Java toolchains audited. |
| **1** | **Minimal Host Plugin** | ✅ **Done** | BepInEx 6 plugin compiled, loaded in ULTRAKILL, logged engine/GPU diagnostics. |
| **2** | **Shared Protocol** | ✅ **Done** | Versioned packet format, serialization, and unit tests passed (C# & Java). |
| **3** | **Interprocess Communication** | 📋 Planned | Shared memory ring buffer, control channel handshake, ping/pong loop. |
| **4** | **Minecraft Guest Mod** | 📋 Planned | Fabric 1.21.1 client mod connects to control channel and completes handshake. |
| **5** | **Framebuffer Capture** | 📋 Planned | Guest captures RGBA8 frames into triple-buffered shared memory. |
| **6** | **Host Rendering** | 📋 Planned | Host reads shared memory frames and presents them on Unity Texture2D. |
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
