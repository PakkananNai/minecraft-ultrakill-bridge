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
│  │   • Shared Memory Frame Reader (Triple-Buffered)      │  │
│  │   • Unity Texture2D & Display Surface                 │  │
│  │   • Input Router & F8 Focus Switcher                  │  │
│  │   • Camera Coordinate Transformer                     │  │
│  └────────────────────────┬──────────────────────────────┘  │
└───────────────────────────┼─────────────────────────────────┘
                            │
              IPC LAYER (Data Plane & Control Plane)
      Shared Memory (/tmp/*.shm) + Localhost Control Channel
                            │
┌───────────────────────────┴─────────────────────────────────┐
│                 Minecraft (Guest Game)                      │
│  Java Edition 1.21.1 / Fabric Loader / OpenJDK 21           │
│                                                             │
│  ┌───────────────────────────────────────────────────────┐  │
│  │   MinecraftFabricMod (Fabric Client Mod)              │  │
│  │   • Framebuffer Capture (Offscreen / Blit)            │  │
│  │   • Shared Memory Writer                              │  │
│  │   • Input Injection & Key Listener                    │  │
│  │   • Camera State Publisher                            │  │
│  │   • Raycast Collision & Entity Service                │  │
│  └───────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

---

## 2. Communication Layer

The bridge splits inter-process communication into two distinct channels:

### A. Data Plane (Shared Memory)
* **Transport:** Memory-mapped file backed by tmpfs (`/tmp/minecraft_ultrakill_bridge.shm`).
* **Cross-Environment Compatibility:** In Wine, Win32 file mapping APIs (`CreateFileMapping`, `MapViewOfFile`) and native Linux `mmap` operate directly on the same kernel page cache when pointing to the same filesystem file. This enables zero-network, high-throughput memory sharing between Wine and native Linux.
* **Architecture:** Triple-buffered framebuffer ring.
  * Buffer states: `FREE (0)`, `WRITING (1)`, `READY (2)`, `READING (3)`.
  * Monotonically increasing sequence counters.
  * Prevents tearing and avoids blocking either the Minecraft render thread or the Unity main thread.
  * If a new frame is not ready, Unity presents the previous valid frame.

### B. Control Plane (Structured Messages)
* **Transport:** Local loopback TCP (`127.0.0.1:<port>`) or Unix domain socket / named pipe.
* **Characteristics:** Low latency (< 0.1 ms on Linux loopback), guaranteed ordering, disconnection detection.
* **Message Framing:**
  ```text
  [Magic: 4B ("MCUB")]
  [Version: 2B (uint16)]
  [Type: 2B (uint16)]
  [SeqId: 4B (uint32)]
  [PayloadLen: 4B (uint32)]
  [Payload Data: N bytes]
  ```
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

---

## 3. Rendering Pipeline

1. **Capture (Guest):** Minecraft renders its frame. A Fabric mixin intercepts the post-render blit, copying raw pixel data (RGBA8 / BGRA8) to the active `WRITING` shared memory buffer.
2. **Commit:** The guest updates the buffer state to `READY` and signals the host with the frame sequence number.
3. **Acquisition (Host):** In `MinecraftBridge.Update()` / `OnRenderObject()`, the plugin acquires the latest `READY` buffer, transitions it to `READING`, and releases the prior buffer to `FREE`.
4. **Display:** The raw bytes are loaded into a Unity `Texture2D` via `LoadRawTextureData` / `Apply()`.
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
