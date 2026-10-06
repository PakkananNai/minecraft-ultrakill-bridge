# Post-M12 Completion Plan — 2026-10-07

**Project:** Minecraft × ULTRAKILL Bridge
**Authority:** `prompt.md` + `AGENTS.md`
**Official roadmap:** M0–M12 only; all twelve are PASS.
**Phase name:** Post-M12 Completion Work
**Method:** Ponytail full — inspect first, reuse existing behavior, smallest working change, no speculative systems.

## 1. What is already finished

The official milestone implementation in `prompt.md` is complete:

- M0 Environment Inspection — PASS
- M1 Minimal Host Plugin — PASS
- M2 Shared Protocol — PASS
- M3 Interprocess Communication — PASS
- M4 Minecraft Guest Mod — PASS
- M5 Framebuffer Capture — PASS
- M6 Host Rendering — PASS
- M7 Input Integration — PASS
- M8 Camera Synchronization — PASS
- M9 Block Interaction and Collision — PASS
- M10 Entity Synchronization — PASS
- M11 Damage and Gameplay Integration — PASS
- M12 Optimization and Stability — PASS

Core architecture is therefore frozen unless a real requirement or defect proves that it must change.

## 2. What “finished” means beyond M12

The final product is not merely a successful bridge process. The project vision in `prompt.md` calls for a **real Minecraft instance that behaves as a playable world integrated into ULTRAKILL**.

Final completion therefore requires evidence that the player can use the existing bridge naturally for Minecraft gameplay and that the integration remains stable on the supported runtime targets.

We will not declare completion based on compilation or protocol coverage alone.

## 3. Remaining work derived directly from the prompt

### Phase A — Prove the existing play controls

Goal: demonstrate that the current M7 input path is sufficient for ordinary Minecraft play.

Verify in a fresh real runtime:

- movement: W/A/S/D
- jump: Space
- sneak: Shift
- mouse look
- hotbar selection: 1–9
- hotbar scrolling: mouse wheel
- inventory: E
- use/interact: right mouse
- attack/break: left mouse
- drop: Q
- focus switch: F8
- held-input release on focus loss

Rule: do not create another input subsystem. Existing `InputBridge` and `GuestInputBridge` remain the default path.

Completion evidence: each control is either verified working or has a concrete reproducible defect.

### Phase B — Close real gameplay gaps

Goal: make Minecraft gameplay usable through the bridge rather than adding duplicate Minecraft logic.

Priority order:

1. Inventory UI input/focus behavior.
2. Hotbar/selected-item behavior.
3. Item use and place behavior.
4. Drop behavior.
5. Entity interaction/attack behavior.
6. Any user-visible gameplay state that the existing Minecraft client already supports but the bridge prevents.

Implementation rule: first try normal Minecraft client semantics through existing input. Add protocol/messages only when a real missing behavior cannot be achieved that way.

Completion evidence: a continuous Minecraft gameplay loop can be performed through the bridge without manual external intervention.

### Phase C — Decide the cross-world movement/collision model

Goal: satisfy the prompt’s world-interaction requirement without building an unnecessary second physics engine.

First determine whether the intended experience actually requires the Minecraft player to collide with or move through ULTRAKILL world geometry.

Decision options:

- **Minecraft-authoritative:** Minecraft movement/collision remains the only gameplay authority; ULTRAKILL presents and hosts the experience.
- **Hybrid:** synchronize only the minimum transform/state required for an actual cross-world interaction.
- **ULTRAKILL-authoritative:** use only if the final gameplay demonstrably requires movement to be controlled by the host.

Ponytail default: choose Minecraft-authoritative unless testing proves otherwise.

Completion evidence: architecture decision is documented and the chosen behavior is runtime-verified.

### Phase D — Confirm the final presentation model

Goal: ensure Minecraft is presented as part of the ULTRAKILL experience, not as an unrelated external window.

Current implementation already renders Minecraft into a Unity surface. Before changing it, verify whether the current presentation satisfies the intended experience.

Only if a real user-visible gap remains:

- improve world/UI placement of the Minecraft surface;
- keep the existing framebuffer transport;
- avoid native graphics interop unless profiling shows that the current path is inadequate.

Completion evidence: visual/runtime verification that Minecraft is presented through ULTRAKILL as intended.

### Phase E — Cross-platform completion

The prompt names two target environments:

- Windows 10/11 native ULTRAKILL + Minecraft.
- Ubuntu 26.04 with Windows ULTRAKILL under Wine/Proton + native Minecraft.

Current project has strong evidence for the Ubuntu/Wine development runtime. Native Windows support is not automatically proven by that evidence.

Next:

1. Build host and guest on the actual target configuration where possible.
2. Verify path/mapping semantics and control transport.
3. Verify rendering and input behavior.
4. Record unsupported differences instead of assuming parity.

Do not add platform-specific code until a real incompatibility is demonstrated.

Completion evidence: a compatibility matrix with PASS / BLOCKED / UNSUPPORTED states backed by tests.

### Phase F — Measured performance closure

The prompt explicitly requires measurement of:

- frame transfer time
- frame upload time
- input latency
- frame sequence gaps
- dropped frames
- CPU usage
- memory usage

Measure first. Optimize only a measured bottleneck.

Current known optimization baseline:

- guest OpenGL fence polling uses `GL_SYNC_FLUSH_COMMANDS_BIT`;
- host framebuffer payload storage is reused instead of allocating a new byte array every frame;
- cross-process framebuffer stress passes 10,000 frames per direction.

Completion evidence: a concise performance baseline and no unexplained regression in normal play.

### Phase G — Stability and lifecycle hardening

Re-run the final integrated loop with:

- fresh startup
- focus switching
- normal gameplay
- disconnect
- reconnect
- restart
- world teardown/change
- clean shutdown

Check:

- no stuck keys/buttons
- no stale framebuffer access
- no leaked mappings
- no protocol desynchronization
- no crashes introduced by integration

Completion evidence: repeatable clean lifecycle with logs from both host and guest.

### Phase H — Final documentation and release checkpoint

Update the project records so they match observed reality:

- completion work log
- final compatibility/performance evidence
- README status
- roadmap status for M0–M12
- final known-issues section

Do not edit `prompt.md` to invent an M13.

A post-M12 phase is an extension of the original project vision, not a hidden official milestone.

## 4. Execution order

The work should proceed in this order, stopping at the first real blocker and fixing it before moving on:

```text
A. Prove controls
      ↓
B. Close gameplay gaps
      ↓
C. Decide/verify cross-world movement & collision
      ↓
D. Confirm presentation
      ↓
E. Verify target-platform compatibility
      ↓
F. Measure performance
      ↓
G. Final lifecycle/stability pass
      ↓
H. Documentation + final release checkpoint
```

Do not implement later work early merely because it sounds useful.

## 5. Per-phase engineering loop

For every phase:

1. Read the authoritative requirements.
2. Inspect existing code and trace the real path.
3. State the smallest change that could solve the observed problem.
4. Implement only that change.
5. Build.
6. Run the smallest relevant automated test.
7. Run real runtime verification where behavior matters.
8. Review for threading, resource ownership, protocol compatibility, and scope.
9. If it fails, identify the root cause and repair it.
10. Repeat until PASS or safely BLOCKED.
11. Update the work log with actual evidence.
12. Commit focused changes.

## 6. Ponytail constraints

- Reuse the existing bridge before adding code.
- Prefer Minecraft’s normal gameplay semantics over custom replicas.
- Prefer existing MCUB messages over adding new message types.
- No new dependencies unless an actual blocker requires one.
- No second physics simulation without a demonstrated requirement.
- No native graphics interop without measurements proving CPU framebuffer presentation is insufficient.
- No speculative entity recreation in Unity.
- No destructive cleanup of unrelated files.
- No fake completion based on compilation.

## 7. Definition of final completion

The Post-M12 work can be declared **DONE** when all are true:

- the core Minecraft gameplay loop is actually usable through ULTRAKILL;
- movement/look/input/focus are runtime-verified;
- inventory, hotbar, use/drop interactions are runtime-verified or explicitly proven unnecessary because the native Minecraft path already provides them;
- block and entity interactions remain correct;
- the chosen cross-world movement/collision model is documented and runtime-verified;
- the final Minecraft presentation is verified inside the ULTRAKILL experience;
- supported-platform status is evidence-backed;
- performance has a measured baseline;
- startup/shutdown/disconnect/reconnect remain clean;
- automated regression tests pass;
- documentation matches reality;
- no known blocker is hidden behind an assumption.

Until then, M0–M12 remain **officially PASS**, while the overall product remains **Post-M12 IN PROGRESS**.

## 8. Immediate next action

Start Phase A with a fresh runtime and prove the existing controls. Do not modify production input code until a real user-visible defect is demonstrated.
