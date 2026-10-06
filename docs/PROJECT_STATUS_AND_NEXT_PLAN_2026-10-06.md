# Minecraft × ULTRAKILL Bridge — Project Status & Next Plan
## Snapshot — 2026-10-06

> **Purpose:** This document is the current project handoff/source-of-truth for what has been built, what is verified, what is blocked, what should be fixed, and what should happen next.
>
> **Primary rules:** `prompt.md` and `AGENTS.md` remain authoritative. A milestone is not complete merely because code compiles; it requires actual testing, review, documentation, and evidence.

## 1. Project goal

The project integrates a **real Minecraft Java Edition 1.21.1 client** into **ULTRAKILL** rather than recreating Minecraft gameplay in Unity.

- ULTRAKILL = host / primary player experience.
- Minecraft = guest / authoritative gameplay simulation.
- Host: C# + BepInEx 6 + Unity 2022.3.
- Guest: Java 21 + Fabric + Minecraft 1.21.1.
- Transport: canonical **MCUB 16-byte TCP control protocol** plus file-backed shared-memory framebuffer/data plane.
- Target runtime: Windows x64 ULTRAKILL under Wine 11 on Ubuntu 26.04.
- Performance matters on the T480; avoid unnecessary copies, blocking render-thread work, and premature native/GPU complexity.

The long-term result should let the player explore Minecraft, look around, break/place blocks, interact with entities, use inventory/tools, and see Minecraft presented directly through ULTRAKILL.

## 2. Architecture that has been established

### Control plane

TCP loopback:

`127.0.0.1:47653`

Responsibilities:
- session negotiation
- liveness
- camera/control messages
- structured MCUB messages
- shutdown/error lifecycle

Canonical MCUB framing uses the fixed **16-byte header**. The old 24-byte/private framing must not be reintroduced.

### Data plane

File-backed shared memory:
- triple-buffered framebuffer
- explicit FREE / WRITING / READY / READING ownership states
- BGRA8 frame payloads
- asynchronous guest OpenGL PBO capture
- host Unity presentation

Shared-memory ownership is **not** represented as TCP messages. GRANT/RELEASE/RESET are data-plane synchronization concepts.

### Host

`host/MinecraftBridge/`
- BepInEx plugin
- Unity lifecycle integration
- input capture
- camera application
- framebuffer consumer/presentation
- TCP control handling

### Guest

`guest/MinecraftFabricMod/`
- Fabric mod
- client-thread input application
- camera sampling
- framebuffer producer
- Minecraft interaction calls

### Important threading rule

Minecraft interaction and client state changes must happen on the Minecraft client thread. Unity-facing state changes must happen on the Unity main thread. Network/background work must not directly mutate either engine's unsafe state.

## 3. Milestone history

| Milestone | Status | What was achieved |
|---|---|---|
| M0 — Environment Inspection | PASS | Ubuntu 26.04, T480 hardware, Wine, ULTRAKILL, BepInEx, Java/Fabric/toolchains audited. |
| M1 — Minimal Host Plugin | PASS | BepInEx 6 plugin builds, loads in ULTRAKILL, and reports diagnostics. |
| M2 — Shared Protocol | PASS | Canonical C#/Java protocol and fixed 16-byte MCUB header established. |
| M3 — Canonical TCP Protocol | PASS | HELLO/HELLO_ACK, PING/PONG, SHUTDOWN and malformed/fragmented/coalesced protocol validation completed. |
| M4 — Fabric Guest Control | PASS | Fabric guest connects, handshakes, and validates START_STREAM mapping identity. |
| M5 — Shared-Memory Framebuffer | PASS | Canonical Java/C# triple buffer and asynchronous BGRA8/PBO capture runtime verified, including reconnect. |
| M6 — Unity Framebuffer Presentation | PASS | Host Unity presentation consumes shared BGRA8 frames and was verified in the live ULTRAKILL runtime. |
| M7 — Input Synchronization | PASS | Unity Input System capture, F8 focus switching, keyboard/mouse/wheel forwarding, client-thread application, and held-input release were verified in the combined runtime. |
| M8 — Camera Synchronization | PASS | Minecraft camera state is sent through MCUB TCP, transformed to Unity coordinates/rotation, applied on Unity main thread, and runtime verified. |
| M9 — Block Interaction & Collision | **PASS** | Real runtime evidence verifies forwarded left-click breaking, held/release behavior, right-click `interactBlock()` success, and Minecraft-authoritative block/collision rules. No speculative cross-world collision system was added. |
| M10 — Entity Synchronization | **PASS** | Entity discovery, movement, spawn/remove, interaction, and reconnect snapshot verified in the real `New World` runtime. |
| M11 — Damage & Gameplay Integration | PASS | Damage event protocol, guest hook, host consumption, serialization, cross-language TCP validation, and live Minecraft → host → ULTRAKILL damage verification passed. |
| M12 — Optimization & Stability | **PASS** | Automated stress/protocol gates plus fresh post-optimization live frame presentation, reconnect/recovery, CPU/RSS sampling, and restart checks passed. |

## 4. Existing evidence/documentation

Important project documents:
- `docs/architecture.md`
- `docs/environment.md`
- `docs/protocol.md`
- `docs/shared_buffer_spec.md`
- `docs/roadmap.md`
- `docs/milestone3_architecture_analysis.md`
- `docs/milestone5_report.md`
- `docs/milestone7_report.md`
- `docs/milestone8_report.md`
- `docs/milestone9_report.md`
- `docs/milestone10_report.md`
- `docs/SESSION_HANDOFF_2026-10-05.md`

The M6/M7/M8 runtime evidence is already recorded. M9 needs an equivalent evidence report after the runtime gate is actually passed.

## 5. Current M9 implementation

The guest receives forwarded mouse events and routes them through Minecraft's normal mouse callback path.

Current conceptual path:

`ULTRAKILL Input`
→ `MCUB INPUT_EVENT`
→ `GuestInputBridge`
→ `MinecraftClient.mouse.onMouseButton()`
→ Minecraft's normal interaction system

This is preferable to permanently bypassing Minecraft's normal mouse handling with direct calls because the goal is to preserve normal client semantics.

M9 also has raycast target logging:
- block target
- block position
- block side
- left/right button
- press/release state

The interaction manager has a null guard during session/world teardown.

## 6. What is verified for M9

### Verified

- Host build succeeds.
- Guest Gradle tests succeed when the runtime is not occupying the test TCP port.
- Cross-language protocol validation succeeds.
- `git diff --check` succeeds.
- Minecraft 1.21.1/Fabric guest loads.
- M9 raycast request/response path has runtime evidence.
- Fresh runtime session `3565680163` connected successfully and validated the shared-memory identity.
- Fresh runtime input reached `GuestInputBridge` and Minecraft's mouse callback on the Render thread.
- `M9_BLOCK_INTERACTION` observed the expected dirt target at `(44,74,-29)`.
- Guest Gradle build is PASS; TCP protocol/control validation is PASS.
- The code is within the intended M9 scope.

### Not verified

These are the actual M9 completion gates:

1. A real forwarded **left click** reaches Minecraft and breaks a target block.
2. A real forwarded **right click** reaches Minecraft and places/uses a target block.
3. Held left-click behavior continues breaking correctly.
4. Release stops block breaking cleanly.
5. The result is observable in the real Minecraft world/log/state.
6. Collision behavior is tested after interaction is proven.

These runtime gates are now demonstrated; see `docs/milestone9_report.md` for the evidence and review.

## 7. The real current problem

The latest runtime test narrowed the blocker substantially.

The bridge path itself worked:
- focus became `true`
- mouse event type `4` reached the guest
- `MinecraftMouseAccessor.onMouseButton()` was invoked
- the expected dirt target was visible to the guest

But the decisive log line was:

`screen=class_433`

`class_433` is Minecraft's `GameMenuScreen`. The integrated server also logged `Saving and pausing game` at the same timestamp. Therefore the click happened while Minecraft was paused. The `M9_BLOCK_INTERACTION` logger only reports the current crosshair target; it does **not** prove that the block was mutated.

This is a **test-state problem, not evidence of an M9 protocol failure**.

Do not rewrite the interaction path for this result. The next runtime attempt must first close the Minecraft pause menu and then repeat the real forwarded interaction test against a known block.

The old external injector failures remain historical context only; the fresh session already demonstrated the intended M7 input path without them.

## 8. Secondary problems discovered

### A. Test/runtime port collision

The guest test suite uses the bridge port and can fail with `BindException` if a live Minecraft runtime is already running.

This is an environment/test-isolation issue, not an M9 protocol regression.

Rule:
- run guest tests with runtime processes stopped, or
- later improve test isolation if the project actually needs concurrent runtime + tests.

Do not confuse this with a production networking failure.

### B. Working-tree clutter

There are unrelated untracked files/directories in the repository, including old validation/POC artifacts and generated files.

Examples observed:
- `README.md.save`
- `dotnet-install.sh`
- `java_sources.txt`
- `job_111220786687_logs.zip`
- `run_logs.zip`
- old validation directories under `tools/`

Do **not** delete them automatically. `AGENTS.md` explicitly forbids destructive cleanup without approval.

Before a milestone commit, separate:
- required source/docs/tests
- deliberate validation tools
- generated artifacts
- unrelated leftovers

Only remove or ignore things after confirming their ownership/purpose.

### C. Documentation drift

README currently says M8 is the latest completed milestone. That is correct as a release-status statement, but M9 work now exists locally.

Do not mark M9 complete in README/roadmap until the runtime gates pass.

This new document is intended to make the current state explicit without falsely changing the official milestone status.

## 9. What should be fixed

### Priority 1 — Fix M9 runtime input validation

Do not add another arbitrary input mechanism first.

Trace the known-good M7 path:

1. ULTRAKILL receives physical keyboard/mouse input.
2. F8 focus switches guest ownership.
3. Unity Input System generates the event.
4. Host converts it to canonical MCUB input.
5. Guest receives it.
6. Guest applies it on the Minecraft client thread.
7. Minecraft's mouse callback processes it.
8. M9 observes the block target and interaction result.

The test should capture evidence at multiple points so failure location is unambiguous.

Minimum useful evidence markers:
- host focus changed
- host input event sent
- guest input event received
- guest input applied
- M9 block target detected
- interaction result
- resulting block/world state

### Priority 2 — Complete M9 interaction tests

Test in this order:

1. Left click on a known breakable block.
2. Hold left click long enough for normal Minecraft breaking.
3. Release.
4. Right click on a known placement surface with a known item.
5. Verify the placed block/item interaction.
6. Repeat after reconnect if practical.
7. Test a miss/no-target case.
8. Test teardown/world-change behavior.

### Priority 3 — Collision

Only after interaction passes:
- verify player collision against Minecraft world geometry.
- verify collision state is synchronized through the intended architecture.
- measure latency and correctness.
- do not invent a second physics simulation if Minecraft remains authoritative.

### Priority 4 — Documentation + commit

When M9 genuinely passes:
- write `docs/milestone9_report.md`
- update `docs/roadmap.md`
- update README milestone table
- record exact test commands/results
- commit only M9-related files
- verify CI
- then continue to the next milestone

## 10. Proposed milestone sequence after M9

Do not implement these early; this is the planned queue.

### M10 — Entity / World Interaction

Likely scope:
- Minecraft entity targeting
- interaction events
- entity state needed by the host
- authoritative Minecraft behavior
- safe synchronization back to ULTRAKILL

Acceptance:
- target an entity in Minecraft
- perform the intended interaction
- observe correct authoritative guest state
- reconnect/teardown remains safe

### M11 — Inventory / Tool Interaction

Likely scope:
- inventory open/close
- hotbar/tool state
- selected item synchronization
- use/drop/pickup interactions

Acceptance:
- player can change selected item
- Minecraft remains authoritative
- UI/input focus is deterministic

### M12 — Integrated Player/World Experience

Likely scope:
- movement/look/interaction integration
- consistent focus transitions
- world-state feedback
- lifecycle/reconnect behavior

Acceptance:
- continuous play loop without manual bridge intervention
- clean startup/shutdown/reconnect
- no obvious desynchronization in normal operation

### Later optimization / polish

Only after functional milestones:
- latency measurements
- dropped-frame measurements
- memory usage
- CPU/GPU cost
- framebuffer copies
- input latency
- reconnect reliability
- profiling-driven optimization

Do not optimize based on guesses.

## 11. Test strategy going forward

Every milestone follows the same loop from AGENTS.md:

1. Read `prompt.md`.
2. Inspect current source/environment.
3. Implement only the current milestone.
4. Build.
5. Run relevant automated tests.
6. Run actual runtime tests.
7. Review architecture and resource/thread ownership.
8. Fix failures.
9. Repeat until evidence is sufficient.
10. Update documentation.
11. Create focused milestone commit.
12. Only then start the next milestone.

A compilation result alone never closes a milestone.

## 12. Git / branch status

Current development branch:

`m5/shared-memory-framebuffer`

The project already contains successful milestone commits through the M8 work. M9 should receive its own focused commit only after the completion gate passes.

Current local M9 source change:
- `guest/MinecraftFabricMod/src/main/java/net/pakkanannai/mcfabricmod/GuestInputBridge.java`

Do not commit unrelated untracked artifacts.

## 13. Immediate next work session

The next session should begin here:

**Step 1:** inspect the actual M7 input implementation and identify the exact runtime event path that produced the existing M7 evidence.

**Step 2:** launch ULTRAKILL + Minecraft using the exact known-good runtime environment.

**Step 3:** trigger F8 and one real mouse click through the M7 path rather than an external synthetic injector.

**Step 4:** collect host + guest logs simultaneously.

**Step 5:** if the event reaches Minecraft but interaction fails, debug M9 interaction semantics.

**Step 6:** if the event never reaches Minecraft, debug focus/input transport instead.

**Step 7:** once left/right interaction is proven, test held break/release.

**Step 8:** test collision.

**Step 9:** write `docs/milestone9_report.md`, update roadmap/README, commit M9, then proceed to M10.

## 14. Definition of "done"

The project should only move forward when all of the following are true:

- implementation matches `prompt.md`
- current milestone acceptance criteria are met
- automated tests pass
- real runtime behavior is demonstrated where required
- no known blocker is being hidden behind a theoretical argument
- reviewer has checked thread/resource/protocol safety
- documentation records what actually happened
- commit contains only the intended milestone work

**Current overall state:**

> **M0–M9: COMPLETE**
>
> **M10: PASS / RUNTIME ENTITY SYNCHRONIZATION VERIFIED**
>
> **M10: committed below as the entity/gameplay transport foundation; M11 is now in progress.**


## 8. M10 completion status

M10 is **PASS**. Guest discovery, entity messages, remove tracking, interaction mixins, host metadata storage, serialization tests, and live runtime gates are verified.

Verified runtime evidence:
- `M10_ENTITY_UPDATE` discovered real frogs, sheep, cows, chickens, pigs, glow squids, and items around the player.
- Entity movement generated repeated position/state updates.
- `M10_ENTITY_REMOVE` was observed for a tracked entity leaving the visible set.
- `M10_ENTITY_INTERACTION` type `2` was observed from the normal Minecraft attack path.
- After the ULTRAKILL host was restarted, the guest reconnected with a new session and emitted a fresh multi-entity `spawned=true` snapshot.
- Guest/host framebuffer identity and M8/M9 traffic continued after reconnect.

M10 is therefore closed. M11 is now the active implementation target.


## 15. Current state override — 2026-10-07

This section supersedes stale planning text earlier in this handoff document that predates M10–M12 completion.

- **M0–M12: PASS.**
- The prompt-defined milestone roadmap ends at M12; there is no M13 in `prompt.md`.
- M12 was closed in `docs/milestone12_report.md` after fresh post-optimization runtime evidence, including sustained M6 frame presentation, reconnect/recovery, resource sampling, and lifecycle checks.
- Guest PBO fence polling now uses `GL_SYNC_FLUSH_COMMANDS_BIT`; host framebuffer payload storage is reused between frames.
- The repository is ready for a separately approved post-M12 functional-expansion phase. Do not silently invent a new milestone or modify the agreed architecture without an explicit phase definition.
- Existing untracked validation/POC artifacts remain untouched by design.

### Post-M12 candidates (planning only)

The long-term vision is broader than the prompt's twelve milestones. The next phase should be selected from actual remaining user-visible gaps, preferably in this order:

1. Validate the existing input path for full Minecraft play controls (movement, hotbar, inventory, use/drop) in a fresh runtime.
2. Decide and document whether ULTRAKILL-side world collision/movement synchronization is actually required; avoid building a second physics simulation unless measured gameplay requires it.
3. Add only user-visible features that cannot already be achieved through Minecraft's authoritative client and the existing bridge.

No implementation is started by this planning section alone.
