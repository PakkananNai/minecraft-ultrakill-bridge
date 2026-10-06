# Milestone 12 — Optimization & Stability Report

## Status

**IN PROGRESS — automated stress/stability gates pass; final live stability/performance gate remains.**

## Scope

M12 focuses on measurable performance and lifecycle hardening without introducing speculative abstractions or a second rendering/data path.

## Work completed

- Reused the existing triple-buffer/PBO path; no new frame-transfer abstraction was introduced.
- Guest control tests use ephemeral loopback ports, preventing runtime/test `47653` collisions.
- Existing mapping lifecycle and cleanup paths were reviewed on both Java and C# sides.
- Existing latest-frame selection drops stale READY frames rather than presenting backlog.
- Existing guest PBO capture is non-blocking on the GPU fence (`glClientWaitSync(..., 0)`), with a bounded three-PBO ring and a single publication worker.

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
- `git diff --check` succeeds.
- Optimized host DLL was deployed to the ULTRAKILL BepInEx plugin directory and hashes match the build artifact.

## Live evidence already observed

The pre-optimization runtime remained stable long enough to present frames and exchange camera/entity/damage traffic. A heartbeat timeout at `03:29:48` was followed by successful guest reconnect and shared-framebuffer identity validation at `03:29:49`. No `M6_FRAME_PRESENT_FAILED` or exception was present in the recent host log. This establishes recovery behavior, but it is not sufficient to close M12 because the optimized DLL has not yet been exercised in a fresh runtime.

## Remaining M12 gate

Restart ULTRAKILL so BepInEx loads the newly deployed optimized DLL, then run Minecraft + ULTRAKILL long enough to verify:

1. sustained frame presentation/capture;
2. reconnect/recovery;
3. CPU/RSS baseline during bridge activity;
4. no repeated capture/presentation/lifecycle errors;
5. clean shutdown/restart.

M12 must not be marked PASS until these post-optimization live checks are observed.
