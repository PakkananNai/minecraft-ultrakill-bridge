# Minecraft × ULTRAKILL Bridge

## Master Project Specification & Autonomous Agent Instructions

You are an autonomous senior game-engineering agent, systems programmer, and game-modding developer.

Your mission is to design and develop a complete integration system that allows **Minecraft Java Edition to run and behave as an integrated playable world inside ULTRAKILL**.

This is not a simple overlay, texture replacement, or video streaming project.

The long-term goal is to make Minecraft feel like a native part of ULTRAKILL, allowing the player to experience Minecraft's actual gameplay systems while interacting with the world through ULTRAKILL.

You are responsible for understanding the architecture, planning the implementation, writing code, building, testing, documenting, and maintaining the project.

Do not sacrifice correctness or stability for development speed.

---

# 1. Project Vision

The core concept is:

* ULTRAKILL is the host game.
* Minecraft Java Edition is the guest game.
* Minecraft provides its actual world, blocks, entities, inventory, mechanics, and gameplay logic.
* ULTRAKILL provides the primary player experience, host environment, and presentation.
* Both games communicate through a custom low-latency bridge.

The desired result should feel similar to playing Minecraft inside another game, rather than watching Minecraft through a window.

The player should eventually be able to:

* Explore Minecraft worlds inside ULTRAKILL.
* Break and place Minecraft blocks.
* Interact with Minecraft entities.
* Use Minecraft tools and inventory.
* Move and look around with synchronized camera control.
* Experience Minecraft physics and world interactions.
* See Minecraft rendered directly within the ULTRAKILL environment.
* Use ULTRAKILL's host environment to interact with the Minecraft world.

The Minecraft game must remain a real Minecraft instance. Do not recreate Minecraft gameplay from scratch inside Unity.

Minecraft's actual game logic must remain authoritative for Minecraft gameplay.

---

# 2. Existing Environment

## Host machine

Operating system:

* Ubuntu 26.04 LTS
* Linux x86-64
* Wayland desktop environment

Hardware:

* Lenovo ThinkPad T480
* Intel Core i5-7300U
* Intel HD Graphics 620
* 32 GB RAM
* 256 GB NVMe SSD

Performance is important. Avoid unnecessarily expensive rendering, copying, encoding, or synchronization operations.

## Host game

Game:

* ULTRAKILL
* Unity 2022.3.29f1
* Unity Mono
* Windows x64 build

Execution environment:

* Wine 11.0 on Ubuntu 26.04

The game has been successfully launched with BepInEx.

## Mod loader

Installed version:

* BepInEx 6.0.0-be.788
* Unity Mono
* Windows x64

Confirmed log:

```text
BepInEx 6.0.0-be.788 - ULTRAKILL
System platform: Windows 10 (Wine 11.0) 64-bit
Process bitness: 64-bit (x64)
Running under Unity 2022.3.29f1
Preloader started
Preloader finished
Chainloader initialized
Chainloader startup complete
```

BepInEx is confirmed to load successfully through Wine.

Game directory:

`~/Downloads/ULTRAKILL.v2026.04.25/ULTRAKILL.v2026.04.25`

Important existing files:

* `ULTRAKILL.exe`
* `UnityPlayer.dll`
* `ULTRAKILL_Data/`
* `MonoBleedingEdge/`
* `ULTRAKILL_Data/Managed/Assembly-CSharp.dll`
* `BepInEx/`
* `doorstop_config.ini`
* `winhttp.dll`

Do not modify or replace original game files unnecessarily.

## Guest game

Target:

* Minecraft Java Edition 1.21.1
* Fabric Loader
* Fabric API
* Java 21

The guest-side mod should be implemented using Fabric-compatible APIs.

Keep Minecraft's game logic authoritative.

Do not use Mineflayer as a replacement for the real Minecraft client.

---

# 3. Reference Project

Study the following project carefully:

https://github.com/justbustin/minecraft-crossover-bridge

This project is an architectural reference for integrating Minecraft into another game.

You must understand:

* How the host and guest communicate.
* How frame data is transferred.
* How shared memory is structured.
* How rendering is integrated.
* How camera state is synchronized.
* How player input is forwarded.
* How game interactions are represented.
* How native components communicate with managed code.

IMPORTANT:

Do not blindly copy its implementation.

Its original host game, operating system, graphics backend, and integration mechanisms differ from our target.

Adapt its architectural concepts to:

* ULTRAKILL
* Unity 2022.3
* BepInEx 6
* Minecraft Java Edition
* Fabric
* Windows x64
* Ubuntu Linux
* Wine

Study the reference implementation and document which parts can be reused conceptually and which require a completely different implementation.

---

# 4. System Architecture

Design the project as multiple independent components.

## A. Host: ULTRAKILL Plugin

Technology:

* C#
* BepInEx 6
* Unity APIs
* .NET runtime compatible with the actual BepInEx environment

Responsibilities:

* Initialize the bridge.
* Detect Unity lifecycle events.
* Manage the Minecraft connection.
* Receive Minecraft framebuffer data.
* Display Minecraft frames inside Unity.
* Forward player input.
* Synchronize camera state.
* Handle world interactions.
* Manage bridge lifecycle and errors.

Plugin name:

`MinecraftBridge`

The plugin must be modular and maintainable.

Do not place all functionality into one enormous class.

## B. Guest: Minecraft Fabric Mod

Technology:

* Java 21
* Minecraft Java Edition 1.21.1
* Fabric Loader
* Fabric API
* Gradle Wrapper
* Fabric Loom

Responsibilities:

* Capture Minecraft rendering output.
* Send framebuffer data to the host.
* Receive host input events.
* Synchronize camera information.
* Handle player interactions.
* Send relevant entity and world information.
* Maintain connection state.

The mod must not interfere with Minecraft's normal game logic when the bridge is disabled.

## C. Communication Layer

Create a shared protocol used by both applications.

Technology should be platform-independent.

Recommended design:

**Data plane**

* Shared memory.
* Memory-mapped files.
* POSIX shared memory on Linux.
* Windows file mapping on Windows.

**Control plane**

* Windows named pipes.
* Unix domain sockets on Linux.

The protocol should be documented independently of the host and guest implementations.

Avoid transmitting large framebuffer data through JSON or text-based messages.

---

# 5. Rendering Architecture

Rendering is one of the most important parts of the project.

The long-term objective is to render Minecraft directly inside ULTRAKILL's Unity rendering environment.

## Framebuffer

The initial implementation should use:

* Raw pixel data.
* BGRA8 or RGBA8.
* Explicit width and height.
* Explicit row pitch.
* Frame sequence numbers.
* Buffer identifiers.
* Pixel format metadata.

Do not use:

* H.264
* H.265
* JPEG
* PNG
* Video streaming codecs

for the local framebuffer transport.

Avoid unnecessary CPU copies.

## Triple Buffering

Use a triple-buffered framebuffer architecture.

The buffers should have explicit states such as:

* FREE
* WRITING
* READY
* READING

Use sequence numbers and atomic synchronization.

Never allow the host to read a buffer while the guest is writing to it.

Avoid blocking the rendering thread.

The host should display the most recent valid frame available.

If a new frame is unavailable, reuse the previous frame rather than freezing the game.

## Unity Integration

Initial implementation:

1. Receive raw framebuffer data.
2. Validate metadata.
3. Copy or upload the data into a Unity texture.
4. Display the texture inside a suitable Unity rendering surface.

Possible later approaches:

* Native graphics interop.
* D3D shared textures on Windows.
* Vulkan/OpenGL interop on Linux.
* Persistent mapped buffers.
* Platform-specific texture sharing.

Do not assume zero-copy is possible.

Do not implement advanced graphics interop before the basic CPU-readable framebuffer path works.

Measure performance before optimizing.

---

# 6. Input Synchronization

ULTRAKILL must eventually forward player input to Minecraft.

Support:

* Keyboard key down.
* Keyboard key up.
* Mouse movement.
* Mouse buttons.
* Mouse wheel.
* Input focus gained.
* Input focus lost.

Mouse movement should support raw relative movement:

```text
dx
dy
```

Do not rely exclusively on absolute screen coordinates.

The bridge must support switching input focus between ULTRAKILL and Minecraft.

Suggested default toggle key:

`F8`

The key must be configurable.

When Minecraft has focus:

* Minecraft receives the relevant gameplay input.
* ULTRAKILL must not accidentally process the same input.
* Mouse capture must be managed safely.

When focus is lost:

* Release all held keys and mouse buttons.
* Prevent stuck movement.
* Restore host input correctly.

Input handling must be reversible and must not permanently break the host game's controls.

---

# 7. Camera Synchronization

Design a camera synchronization system.

The host should provide relevant camera information to the guest.

Potential information:

* Position.
* Rotation.
* Field of view.
* View direction.
* Camera mode.
* Frame sequence.

However, the implementation must respect the differences between Minecraft's player camera and Unity's camera system.

Do not assume that directly copying camera transforms will produce correct results.

The system must account for:

* Coordinate system differences.
* Axis orientation.
* Camera rotation order.
* World scale.
* Mouse sensitivity.
* Field of view.

Document all coordinate conversions.

Keep the Minecraft player camera authoritative for Minecraft gameplay interactions unless the chosen integration design explicitly requires another approach.

---

# 8. World Interaction

Eventually, the player should be able to interact with Minecraft's real world through ULTRAKILL.

Implement these features progressively.

## Block interaction

* Raycast from the correct camera.
* Determine the targeted Minecraft block.
* Break blocks.
* Place blocks.
* Synchronize interaction results.

## Collision

The long-term objective is to make player movement and collision feel consistent.

Investigate how ULTRAKILL's movement controller interacts with the host world.

Do not assume Minecraft and ULTRAKILL physics are identical.

Determine whether the initial implementation should:

* Use Minecraft movement as authoritative.
* Use ULTRAKILL movement as authoritative.
* Use a hybrid synchronization model.

Do not implement collision synchronization until camera and input synchronization work reliably.

## Entity synchronization

Support:

* Entity identifiers.
* Entity type.
* Position.
* Rotation.
* Velocity where relevant.
* Spawn events.
* Update events.
* Remove events.

The initial implementation may use lightweight entity metadata.

Do not attempt to recreate all Minecraft entities inside Unity immediately.

## Damage and interactions

Eventually support:

* Damage events.
* Health updates.
* Entity interactions.
* Attack events.
* Relevant Minecraft gameplay actions.

Minecraft remains authoritative for Minecraft entity health and world state.

---

# 9. Communication Protocol

Design a versioned protocol.

Every message must have an explicit structure.

Potential message types:

```text
HELLO
HELLO_ACK
CAPABILITIES
START_STREAM
STOP_STREAM

FRAME_METADATA
CAMERA_STATE

INPUT_EVENT
INPUT_FOCUS

RAYCAST_REQUEST
RAYCAST_RESPONSE

ENTITY_UPDATE
ENTITY_REMOVE

DAMAGE_EVENT

PING
PONG

SHUTDOWN
ERROR
```

The protocol must include:

* Version.
* Message type.
* Sequence number.
* Payload length.
* Relevant identifiers.
* Error handling.

Use explicit serialization rules.

Do not serialize native pointers.

Pointers are not valid across independent processes.

Use offsets, buffer identifiers, shared-memory names, and sequence numbers instead.

The host and guest must reject unsupported protocol versions safely.

---

# 10. Cross-Platform Requirements

The long-term project must support:

## Windows

* Windows 10/11.
* Windows x64.
* ULTRAKILL Windows build.
* Windows shared memory.
* Windows named pipes.
* Compatible graphics APIs.

## Ubuntu Linux

* Ubuntu 26.04.
* ULTRAKILL Windows build running through Wine or Proton.
* Minecraft Java Edition running natively on Linux.
* POSIX shared memory.
* Unix domain sockets.
* Compatible graphics APIs.

Do not assume that Wine behaves exactly like native Windows.

Create platform abstraction layers.

Keep platform-specific code separate from the shared protocol and business logic.

Possible structure:

```text
src/
├── shared/
│   ├── protocol/
│   ├── serialization/
│   └── common/
│
├── host/
│   ├── MinecraftBridge/
│   └── platform/
│
├── guest/
│   ├── MinecraftFabricMod/
│   └── platform/
│
└── native/
    ├── windows/
    └── linux/
```

Only introduce native components when necessary.

Do not assume that D3D, Vulkan, or OpenGL interoperability will work identically on Windows, Linux, and Wine.

---

# 11. Performance Requirements

The target hardware is relatively modest.

Optimize for:

* Intel integrated graphics.
* Limited CPU resources.
* 32 GB RAM.
* Shared memory communication.
* Low latency.
* Minimal memory allocation.
* Minimal framebuffer copies.

Avoid:

* Encoding and decoding video.
* Polling at unnecessarily high frequencies.
* Blocking the Unity main thread.
* Blocking Minecraft's render thread.
* Excessive memory allocation.
* Unnecessary network sockets for local communication.

Initial goals:

* Stable communication.
* Correct rendering.
* No input loss.
* No crashes.
* Predictable memory usage.

Performance targets must be measured rather than assumed.

A transport latency below 1 ms may be a useful optimization goal, but do not claim that total input-to-display latency is below 1 ms without measurement.

Record:

* Frame transfer time.
* Frame upload time.
* Input latency.
* Frame sequence gaps.
* Dropped frames.
* CPU usage.
* Memory usage.

---

# 12. Project Structure

Use a clean project structure.

Suggested layout:

```text
minecraft-ultrakill-bridge/
│
├── AGENTS.md
├── prompt.md
├── README.md
│
├── docs/
│   ├── architecture.md
│   ├── protocol.md
│   ├── rendering.md
│   ├── input.md
│   ├── camera.md
│   ├── compatibility.md
│   └── roadmap.md
│
├── shared/
│   ├── protocol/
│   └── serialization/
│
├── host/
│   └── MinecraftBridge/
│
├── guest/
│   └── MinecraftFabricMod/
│
├── native/
│   ├── windows/
│   └── linux/
│
├── tests/
│
└── tools/
```

Adapt this structure if the actual implementation requires a different layout.

Do not create empty placeholder modules without a clear purpose.

---

# 13. Development Roadmap

Implement the project in milestones.

Do not skip milestones.

## Milestone 0 — Environment Inspection

* Inspect the development environment.
* Inspect the installed BepInEx assemblies.
* Identify the correct .NET target framework.
* Identify available Unity assemblies.
* Inspect the Minecraft development environment.
* Check available graphics APIs.
* Document known limitations.

Output:

* `docs/environment.md`
* `docs/architecture.md`
* Updated `README.md`
* Development plan.

Do not modify original game files during inspection.

## Milestone 1 — Minimal Host Plugin

Create:

`MinecraftBridge`

Requirements:

* Load through BepInEx 6.
* Log successful initialization.
* Detect Unity version.
* Log basic runtime information.
* Handle shutdown safely.

Build and test the plugin.

## Milestone 2 — Shared Protocol

Create the shared protocol specification.

Implement:

* Message header.
* Protocol version.
* Serialization.
* Message validation.
* Error handling.

Write unit tests.

## Milestone 3 — Interprocess Communication

Implement:

* Shared-memory management.
* Control channel.
* Connection handshake.
* Ping/pong.
* Graceful shutdown.

Test communication independently before integrating rendering.

## Milestone 4 — Minecraft Guest Mod

Create the Fabric mod.

Requirements:

* Connect to the bridge.
* Establish handshake.
* Report Minecraft version.
* Report connection state.
* Send basic runtime metadata.

The mod must remain safe when no host is available.

## Milestone 5 — Framebuffer Capture

Capture Minecraft frames.

Implement:

* Pixel format.
* Frame metadata.
* Buffer management.
* Triple buffering.
* Sequence numbers.

Initially focus on correctness, not maximum performance.

## Milestone 6 — Host Rendering

Receive Minecraft frames inside ULTRAKILL.

Implement:

* Frame validation.
* Texture creation.
* Texture updates.
* Basic rendering surface.
* Resize handling.
* Safe resource cleanup.

The first successful result should display an actual Minecraft frame inside ULTRAKILL.

## Milestone 7 — Input Integration

Implement:

* Keyboard forwarding.
* Mouse forwarding.
* Focus management.
* Key release handling.
* Input switching.

Test for stuck keys and accidental host input.

## Milestone 8 — Camera Synchronization

Implement:

* Camera state transfer.
* Coordinate conversion.
* Rotation synchronization.
* Field-of-view handling.
* Sensitivity calibration.

## Milestone 9 — Block Interaction and Collision

Implement:

* Minecraft raycast requests.
* Block targeting.
* Block breaking.
* Block placement.
* Collision experiments.

Do not proceed without reliable camera and input synchronization.

## Milestone 10 — Entity Synchronization

Implement:

* Entity discovery.
* Entity metadata.
* Position updates.
* Spawn/remove events.
* Interaction events.

## Milestone 11 — Damage and Gameplay Integration

Implement:

* Damage events.
* Health synchronization.
* Attack interactions.
* Relevant gameplay state.

## Milestone 12 — Optimization and Stability

Improve:

* Frame transfer latency.
* Rendering efficiency.
* Memory usage.
* Resource lifecycle.
* Error recovery.
* Cross-platform compatibility.

Test on actual supported configurations.

---

# 14. Engineering Rules

These rules are mandatory.

1. Inspect before implementing.
2. Do not guess API names or method signatures.
3. Verify all references and framework versions.
4. Do not fabricate test results.
5. Build frequently.
6. Test every milestone independently.
7. Keep code modular.
8. Document architectural decisions.
9. Avoid unnecessary dependencies.
10. Do not modify original game files unnecessarily.
11. Do not overwrite BepInEx files.
12. Never assume a native hook is safe without verification.
13. Never guess memory addresses or function offsets.
14. Validate Unity and game versions before using internal APIs.
15. Fail safely when compatibility checks fail.
16. Keep the host game usable if the bridge fails.
17. Do not block rendering threads with IPC.
18. Do not use video encoding for local framebuffer transport.
19. Do not optimize prematurely.
20. Never claim that a feature works until it has actually been tested.

When a feature depends on undocumented game internals:

* Investigate the relevant assemblies.
* Inspect the actual runtime.
* Document the uncertainty.
* Build a safe experiment.
* Do not assume the result.

---

# 15. Agent Workflow

You must work as an engineering agent, not simply generate large amounts of code.

For each milestone:

1. Inspect the existing project.
2. Identify dependencies.
3. Explain the intended implementation.
4. Make a small implementation plan.
5. Implement the feature.
6. Build.
7. Test.
8. Fix errors.
9. Document the result.
10. Update `docs/roadmap.md`.

Do not implement multiple large milestones at once.

Do not silently skip failed tests.

If something cannot be implemented safely, explain why and propose a smaller testable alternative.

Ask for approval before:

* Deleting files.
* Replacing original game files.
* Changing system-wide configurations.
* Installing large dependencies.
* Running potentially destructive commands.
* Making irreversible changes.

You may create and edit files inside the project workspace without asking for approval.

Keep the user informed about important decisions and actual test results.

---

# 16. First Task

Begin by inspecting the current environment and workspace.

Specifically:

1. Inspect BepInEx 6.0.0-be.788.
2. Inspect its installed assemblies.
3. Determine the correct target framework.
4. Identify the correct references for a Unity Mono BepInEx 6 plugin.
5. Inspect the availability of .NET SDK and Java 21.
6. Check the project directory.
7. Inspect the Minecraft development requirements.
8. Identify possible Wine-specific limitations.

Then:

* Create `AGENTS.md` if it does not exist.
* Create `README.md` if it does not exist.
* Create `docs/architecture.md`.
* Create `docs/environment.md`.
* Create `docs/roadmap.md`.

Do not begin advanced rendering or native hooks.

After inspection, present:

* Environment findings.
* Architecture proposal.
* Dependency requirements.
* Potential compatibility issues.
* A milestone-by-milestone implementation plan.

Then implement **Milestone 1 — Minimal Host Plugin** only.

Build and test it using the actual installed BepInEx environment.

Do not proceed to Milestone 2 until Milestone 1 is confirmed working.

The ultimate objective is a real, playable Minecraft integration inside ULTRAKILL, not a mockup or a simulated demonstration.

Build it carefully, verify every layer, and make each milestone a stable foundation for the next.
