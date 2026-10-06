# Milestone 12 — Optimization & Stability Report

## Status

**PASS — automated gates and post-optimization live stability/recovery checks passed.**

## Scope

M12 focuses on measurable performance and lifecycle hardening without introducing speculative abstractions or a second rendering/data path.

## Work completed

- Reused the existing triple-buffer/PBO path; no new frame-transfer abstraction was introduced.
- Guest control tests use ephemeral loopback ports, preventing runtime/test `47653` collisions.
- Existing mapping lifecycle and cleanup paths were reviewed on both Java and C# sides.
- Existing latest-frame selection drops stale READY frames rather than presenting backlog.
- Guest PBO capture uses non-blocking GPU fence polling with `GL_SYNC_FLUSH_COMMANDS_BIT`, with a bounded three-PBO ring and a single publication worker.

## Automated evidence

### Shared framebuffer stress

`FRAMES=10000 ./tools/shared_framebuffer_validation/run_cross_process_stress.sh`

**PASS** — 10,000 frames in each direction with checksum/payload validation and latest-frame supersession.

Observed:
- C# → Java: final 10,000 consumed.
- Java → C#: final 10,000 consumed.
- Process exit code 0.
- Runtime: 13.74 s.

### TCP/control stability

`./tools/tcp_protocol_validation/run.sh`

**PASS** — canonical 16-byte TCP protocol, cross-language fixtures, normal/fragmented/coalesced/malformed/disconnect/idle cases, shared-memory lifecycle, and process cleanup.

Evidence:
`tools/tcp_protocol_validation/logs/20261006T204224Z-87686/`

### Guest test isolation

Guest Gradle tests pass after test-only loopback servers were changed from fixed port `47653` to OS-assigned ephemeral ports.

## Design decision

No speculative frame-rate limiter, custom allocator, profiler framework, or alternate rendering path was added. The current implementation already uses latest-frame selection, asynchronous PBO readback, bounded PBO count, and explicit shared-buffer ownership. Further optimization should be driven by live measurements rather than guessed bottlenecks.

## Current automated gate

PASS:
- Host optimization build succeeds with Wine Mono `mcs`.
- Guest Gradle test suite succeeds.
- TCP protocol validation succeeds with canonical 16-byte header, cross-language fixtures, malformed/fragmented/coalesced cases, lifecycle, and cleanup.
- 10,000-frame cross-process shared-memory stress passes in both C#→Java and Java→C# directions.
- `git diff --check` succeeds.
- Optimized host DLL builds successfully and was deployed before the fresh live runtime.

## Live evidence

Fresh post-optimization runtime evidence was collected after deploying host commit `54dd280` and guest commit `2a15923`.

- Fresh host process loaded `MinecraftBridge v0.1.0` and created the M6 render surface, M7 input backend, and M8 camera bridge.
- Guest sessions `2568775031`, `134743783`, and `3895702850` established successfully with shared-framebuffer identity validation.
- M6 frame presentation continued through `rendered=297` with 854x480 BGRA8 payloads and no `M6_FRAME_PRESENT_FAILED`.
- Session `134743783` was intentionally/externally reset by the guest and the host recovered to session `3895702850`, demonstrating reconnect/recovery while frame presentation continued.
- CPU/RSS sample during active bridge runtime: ULTRAKILL `138% CPU`, `1.21 GiB RSS`; Minecraft `112% CPU`, `1.50 GiB RSS`.
- The only guest GL error observed was Wayland cursor-position limitation (`65548`), unrelated to framebuffer capture.
- Host-side bridge warnings after the reset were limited to the expected connection-reset write/raycast warnings for the closed session; no frame presentation failure or bridge exception occurred.
- The fresh runtime was restarted successfully after the optimization deployment and remained active with live frame exchange.

## Final gate

**PASS.** The post-optimization runtime satisfied sustained frame presentation, reconnect/recovery, CPU/RSS sampling, no repeated framebuffer failure, and restart/lifecycle checks. M12 is complete.
