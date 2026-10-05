# Milestone 5 — Framebuffer Capture Report

**Status: PASS / Complete**

## Scope

M5 implements the canonical file-backed triple-buffer data plane and the Minecraft 1.21.1 Fabric
framebuffer producer. Unity texture presentation is deferred to M6.

## Implementation

- Canonical 128-byte mapping and slot headers; 3 slots; BGRA8; fixed 4K slot capacity.
- `START_STREAM` ID 6 advertises mapping path, session ID, and 128-bit generation.
- Guest validates mapping size/header/session/generation before data-plane use.
- Minecraft uses three OpenGL pixel-pack buffers and zero-timeout GPU fences.
- Completed PBOs are copied/published by one worker thread.
- Render-thread slot reservation is atomic and non-blocking with respect to the frame-sized worker copy.
- Sequence numbers and checksums are published with each frame.
- Reconnect abandons the old mapping and accepts a fresh mapping/generation.

## Validation Evidence

### Unit / build

- Fabric `clean test`: PASS. 14 tests across GuestControlClient, SharedFramebuffer, and START_STREAM suites; 0 failures/errors.
- Fabric `build`: PASS.
- Host plugin build: PASS; Wine Mono emitted expected XDG/Wine window warnings but produced `bin/MinecraftBridge.dll`.
- Cross-language packet fixtures: PASS; all 11 canonical fixtures matched.
- Cross-process shared-buffer stress: PASS; 2,000 frames in each Java↔C# direction.

### Live Minecraft 1.21.1

- Actual Fabric 1.21.1 client launched with the production guest mod.
- Live BGRA8 publication observed at **854x480** in an earlier run.
- Live BGRA8 publication observed at **1366x700** in the final run.
- Final run observed **267 published frames**, ending at sequence **267**.
- Payload length: **3,824,800 bytes**; stride: **5,464 bytes**; format: **BGRA8**.
- Checksums were non-zero and varied across frames.
- All three shared slots were exercised.
- A reconnect run published **9 frames on the first mapping and 9 frames on the second**, with fresh session IDs/generations and continued live capture.
- Client logs showed successful mapping identity validation and no framebuffer-capture exceptions/errors.

## Explicit non-claims

- 3840x2160 GPU throughput is not claimed on this 1366x768 development display.
- Unity texture presentation and ULTRAKILL-side consumption are M6 work.
- The runtime reconnect test simulates host disconnect by terminating the loopback harness; it is not a full ULTRAKILL process-crash test.
