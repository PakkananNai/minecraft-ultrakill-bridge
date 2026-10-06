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

## Remaining M12 gate

Run the actual Minecraft 1.21.1 + ULTRAKILL runtime and collect:

1. sustained frame presentation/capture evidence;
2. reconnect/recovery evidence;
3. process memory/CPU baseline during bridge activity;
4. absence of repeated capture/presentation/lifecycle errors;
5. clean shutdown and restart.

M12 must not be marked PASS until these live stability checks are observed.
