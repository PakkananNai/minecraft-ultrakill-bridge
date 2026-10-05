# System Architecture Specification

## 1. Overview & System Components

The **Minecraft × ULTRAKILL Bridge** integrates a full, authentic Minecraft Java Edition game instance into ULTRAKILL running under Unity 2022.3 on Wine / Linux.

```
┌─────────────────────────────────────────────────────────────┐
│                 ULTRAKILL (Host Game)                       │
│  Unity 2022.3.29f1 (Mono x64) under Wine 11.0               │
│                                                             │
│  ┌───────────────────────────────────────────────────────┐  │
│  │   MinecraftBridge Plugin (BepInEx 6 Unity Mono)       │  │
│  │   • Lifecycle & Unity Hooking                         │  │
│  │   • Shared Framebuffer Reader (Milestone 6)              │  │
│  │   • Unity Texture2D & Display Surface                 │  │
│  │   • Input Router & F8 Focus Switcher                  │  │
│  │   • Camera Coordinate Transformer                     │  │
│  └────────────────────────┬──────────────────────────────┘  │
└───────────────────────────┼─────────────────────────────────┘
                            │
              IPC LAYER (Data Plane & Control Plane)
      File-backed Shared Memory + Localhost Control Channel
                            │
┌───────────────────────────┴─────────────────────────────────┐
│                 Minecraft (Guest Game)                      │
│  Java Edition 1.21.1 / Fabric Loader / OpenJDK 21           │
│                                                             │
│  ┌───────────────────────────────────────────────────────┐  │
│  │   MinecraftFabricMod (Fabric Client Mod)              │  │
│  │   • BGRA8 Framebuffer Capture (PBO / GPU Fence)          │  │
│  │   • Shared Triple-Buffer Writer                            │  │
│  │   • Input Injection & Key Listener                    │  │
│  │   • Camera State Publisher                            │  │
│  │   • Raycast Collision & Entity Service                │  │
│  └───────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

---

## 2. Communication Layer

The bridge splits inter-process communication into two distinct channels:

The architecture diagram describes the target system. The repository contains the host plugin, canonical shared protocol, Fabric guest control client,
M5 shared-buffer primitives, and the asynchronous Minecraft framebuffer capture implementation.
M5 capture has been runtime-verified in the configured Minecraft 1.21.1 client; host presentation
is Milestone 6.

### A. Data Plane (Shared Memory)
* **Transport:** File-backed memory mapping. In the current Wine prefix, the default configuration creates the file under native `/home/pakkanannai/Downloads` through the existing `Z:` drive mapping; both Linux and Wine directories are configurable in BepInEx settings, and the guest receives the native Linux absolute path.
* **Cross-Environment Compatibility:** In Wine, Win32 file mapping APIs (`CreateFileMapping`, `MapViewOfFile`) and native Linux `mmap` operate directly on the same kernel page cache when pointing to the same filesystem file. This enables zero-network, high-throughput memory sharing between Wine and native Linux.
* **Milestone 3:** File-backed mapping creation/opening, size validation, and cleanup only. This was the original M3 scope; M5 now defines and implements the production slot layout and ownership.
* **M5 implementation:** Canonical triple-buffered framebuffer primitives now exist on both
  the Java guest side and C# host side using the shared 128-byte headers, 33,177,600-byte
  BGRA8 slot capacity, generation fencing, atomic state transitions, and latest-frame selection.
  A C#↔Java cross-process stress test passes 2,000 frames in each direction against the same
  99,533,312-byte file mapping, including metadata, checksum, payload, and latest-frame validation.
* **M5 mapping identity integration:** the host creates and initializes one file-backed mapping per
  control session, then advertises its Linux path, session ID, and immutable generation through the
  existing `START_STREAM` message. The guest opens the file and validates its size, header, session,
  and generation before exposing it to data-plane code.
* **M5 framebuffer capture:** the Fabric guest registers `HudRenderCallback` after HUD rendering,
  reads the main framebuffer as BGRA8 through a three-entry OpenGL PBO ring, polls fences with a
  zero-timeout wait, and publishes completed mapped buffers on one worker thread. Render-thread slot
  reservation uses atomic state transitions and does not wait for the worker's frame-sized copy.
  Rows retain OpenGL bottom-left origin.
* **M5 runtime validation:** the configured Minecraft 1.21.1 Fabric development client now runs the
  actual capture path. A loopback consumer observed 267 consecutive published frames at 1366x700
  with BGRA8 metadata/payload/checksum validation; a prior 854x480 run also published live frames.
  A reconnect run accepted two fresh session IDs/generations and published 9 frames on each mapping.
  The current Wine/Linux shared-path pair has been verified. Unity host presentation remains
  Milestone 6, and 3840x2160 GPU throughput is intentionally not claimed on this display.

### B. Control Plane (Structured Messages)
* **Transport:** Local loopback TCP (`127.0.0.1:47653`) for the current host runtime.
* **Responsibilities:** Canonical MCUB framing, HELLO/HELLO_ACK negotiation, control messages, PING/PONG liveness, SHUTDOWN, and TCP connection lifecycle.
* **Session policy:** A session ID is allocated per accepted control connection and returned in HELLO_ACK. It is control-session metadata; it is not put in the frame header and does not fence shared-memory users.
* **Characteristics:** TCP provides ordered bytes per connection; framing is defined by MCUB. Socket operations have bounded timeouts and close on malformed/incompatible frames.
* **Message Framing:**
  ```text
  [Magic: 4B ("MCUB")]
  [Version: 2B (uint16)]
  [Type: 2B (uint16)]
  [SeqId: 4B (uint32)]
  [PayloadLen: 4B (uint32)]
  [Payload Data: N bytes]
  ```
  The MCUB header is exactly 16 bytes, little-endian, as defined in `docs/protocol.md`.
* **Message Types:**
  * `HELLO` / `HELLO_ACK` (Version and capability negotiation)
  * `PING` / `PONG` (Heartbeat and latency tracking)
  * `START_STREAM` / `STOP_STREAM`
  * `FRAME_METADATA`
  * `CAMERA_STATE`
  * `INPUT_EVENT` / `INPUT_FOCUS`
  * `RAYCAST_REQUEST` / `RAYCAST_RESPONSE`
  * `ENTITY_UPDATE` / `ENTITY_REMOVE`
  * `SHUTDOWN`

MCUB does not grant or release shared-memory slots. Buffer state, ownership, and synchronization remain local to the shared-memory design; `START_STREAM` advertises only mapping identity. Guest reconnect creates a new control session and mapping generation rather than reclaiming slots from an abandoned mapping.

---

## 3. Rendering Pipeline

1. **Capture (Guest):** After HUD rendering, Fabric reads the Minecraft main framebuffer as BGRA8 into a pixel-pack buffer and inserts a GPU fence.
2. **Commit:** Once the fence signals, a worker thread copies the mapped PBO bytes into an exclusively reserved shared slot, computes the checksum, and publishes `READY` with its sequence number.
3. **Acquisition (Host, Milestone 6):** The future Unity consumer will acquire the latest `READY` slot, transition it to `READING`, and release the prior slot to `FREE`.
4. **Display (Milestone 6):** The host will upload the raw bytes to a Unity `Texture2D`; vertical orientation must account for the OpenGL bottom-left row origin.
5. **Presentation:** The texture is projected onto a custom screen / camera overlay or world quad inside ULTRAKILL.

---

## 4. Input & Focus Management

* **Toggle Key:** `F8` (configurable via BepInEx configuration file).
* **State Management:**
  * When ULTRAKILL has focus: Normal ULTRAKILL mouse look and keyboard control. Minecraft receives neutral/idle inputs.
  * When Minecraft has focus: Host locks/suppresses local input handling; keyboard key down/up, mouse delta (`dx, dy`), mouse buttons, and scroll wheel are forwarded to Minecraft.
* **Fail-Safe Release:** On focus switch or disconnection, all held keys and mouse buttons are forcibly released to eliminate stuck input states.

---

## 5. Camera Synchronization & Coordinate Systems

* **Minecraft Coordinate System:**
  * Right-handed coordinate system.
  * $X$ is East, $Y$ is Up, $Z$ is South.
  * Yaw ($0^\circ = +Z$, $90^\circ = -X$, $180^\circ = -Z$, $270^\circ = +X$), Pitch ($-90^\circ$ up, $+90^\circ$ down).
* **Unity Coordinate System:**
  * Left-handed coordinate system.
  * $X$ is Right, $Y$ is Up, $Z$ is Forward.
  * Yaw, Pitch, Roll in standard Unity Euler angles.
* **Transform Function:**
  $$X_{\text{Unity}} = -X_{\text{MC}}, \quad Y_{\text{Unity}} = Y_{\text{MC}}, \quad Z_{\text{Unity}} = Z_{\text{MC}}$$
  Transform functions are rigorously tested with unit tests to prevent disorientation or inverted axes.

---

## 6. Lessons from Reference Implementation (`minecraft-crossover-bridge`)

| Feature | Reference (`minecraft-crossover-bridge`) | Our Project (`minecraft-ultrakill-bridge`) |
| :--- | :--- | :--- |
| **Host Game** | Monster Hunter: World / Elden Ring | ULTRAKILL |
| **Engine / Tech** | Custom C++ Engines (MT Framework / Danse Macabre) | Unity 2022.3.29f1 (Mono x64) |
| **Host Mod Loading** | Direct `dinput8.dll` DLL hijacking | BepInEx 6 Unity Mono plugin |
| **Target OS / Wine** | macOS Apple Silicon + CrossOver + DXMT/D3DMetal | Ubuntu 26.04 x86_64 + Wine 11.0 |
| **Rendering Interop** | D3D11 / D3D12 native hook into swapchain | Unity `Texture2D` + Camera overlay / UI Quad |
| **IPC File Mappings** | Shared memory in `/tmp/` mapped by both sides | Identical concept adapted for Linux Wine tmpfs |
| **Camera Authority** | Overrides host camera memory pointers directly | Unity camera controller sync via C# plugin |


### Minecraft framebuffer producer (M5 complete)

The Fabric guest captures the main Minecraft framebuffer after HUD rendering. It issues BGRA8
`glReadPixels` into a three-entry OpenGL pixel-pack-buffer ring and fences each GPU readback.
Fence polling uses a zero-timeout wait; completed PBOs are mapped only after the fence signals.
A single daemon publisher thread copies the mapped bytes into an exclusively reserved shared slot,
computes the configured FNV-1a checksum, and publishes READY. If no shared slot or PBO is free,
the frame is dropped rather than blocking the render thread. OpenGL read framebuffer, read buffer,
pixel-pack binding, and pack layout state are restored after each submission.

Pixel rows retain OpenGL bottom-left origin; the Unity consumer must account for this when mapping
the frame onto a texture. The Fabric mod builds and its Java tests pass. The configured 1.21.1 runtime is available in the
project's development run environment and the actual post-HUD PBO capture path has been exercised.
A loopback consumer observed live BGRA8 frames at 1366x700 and an earlier 854x480 run. ULTRAKILL
Texture2D presentation remains Milestone 6 and is intentionally not claimed here.
