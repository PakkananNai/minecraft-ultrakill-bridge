# Minecraft × ULTRAKILL Bridge

A bridge architecture allowing **Minecraft Java Edition** to run and behave as an integrated, playable world inside **ULTRAKILL**.

---

## High-Level Vision

* **Host Game:** ULTRAKILL (Unity 2022.3.29f1 Mono x64 running through Wine 11.0 on Ubuntu 26.04 LTS).
* **Guest Game:** Minecraft Java Edition 1.21.1 (Fabric Loader 0.16.5, Java 21).
* **Core Philosophy:** Real Minecraft logic and world authority, rendered and presented through ULTRAKILL's host environment with synchronized camera, low-latency framebuffer transport, and seamless input routing.

---

## Project Structure

```text
minecraft-ultrakill-bridge/
├── AGENTS.md                   # Operational rules and agent instructions
├── prompt.md                   # Master engineering specification & roadmap
├── README.md                   # Project overview and status
├── docs/                       # Project documentation
│   ├── environment.md          # Environment audit & verified findings
│   ├── architecture.md         # System architecture & inter-process pipeline
│   ├── protocol.md             # Canonical MCUB wire protocol
│   ├── roadmap.md              # Milestone tracking & implementation status
│   └── milestone3_architecture_analysis.md  # Sync-candidate analysis
├── shared/                     # Shared IPC protocol and serialization (C# / Java)
├── host/                       # ULTRAKILL host plugin (BepInEx 6 Unity Mono)
│   └── MinecraftBridge/
├── guest/                      # Minecraft Fabric mod (Java 21)
│   └── MinecraftFabricMod/
├── native/                     # Optional native helper libraries (POSIX / Win32)
├── tests/                      # Unit & integration tests
└── tools/                      # Build and verification scripts
```

---

## Architecture (Architecture B)

The bridge separates communication into two distinct channels:

**TCP control plane → shared-memory synchronization/data plane → framebuffer producer/consumer.**

* **TCP control plane (loopback `127.0.0.1:47653`):** Session negotiation, liveness, and structured control messages framed with the canonical MCUB header. The endpoint handles `HELLO`/`HELLO_ACK`, `PING`/`PONG`, `START_STREAM` mapping identity advertisement, `ERROR`, and one-way `SHUTDOWN`.
* **Shared-memory data plane:** Frame bytes and producer/consumer synchronization state live in a file-backed mapping under the configured Wine/Linux shared directory. This plane carries the actual framebuffer payload; only compact metadata/control events cross TCP.
* **Framebuffer producer/consumer:** The Fabric guest captures BGRA8 frames asynchronously through an OpenGL PBO ring and publishes them to the shared triple buffer. Host-side C# consumer primitives are implemented; Unity texture presentation is Milestone 6.

The shared-memory buffer state model (e.g. `FREE`/`WRITING`/`READY`/`READING`) is **shared-memory state**, not a TCP message. Likewise, ownership and lifetime operations such as GRANT, RELEASE, and RESET are data-plane synchronization concepts and are **not** TCP messages. MCUB does not grant or release shared-memory slots, and a new TCP session ID is not a shared-memory fencing mechanism.

### Canonical MCUB Header

Every control-channel packet is a fixed **16-byte little-endian** header followed by a variable-length payload:

```text
[Magic: 4B ("MCUB")] [Version: 2B (uint16)] [Type: 2B (uint16)]
[SeqId: 4B (uint32)] [PayloadLen: 4B (uint32)] [Payload: N bytes]
```

The full message set, payload layouts, and validation rules are defined in `docs/protocol.md`.

---

## Current Status

| Milestone | Status | Notes |
| :--- | :--- | :--- |
| **M0 — Environment Inspection** | ✅ Complete | Environment, Wine, BepInEx, and toolchains audited. |
| **M1 — Minimal Host Plugin** | ✅ Complete | BepInEx 6 plugin builds, loads, and logs engine/GPU diagnostics. |
| **M2 — Shared Protocol** | ✅ Complete | Canonical C#/Java codecs and the fixed 16-byte MCUB header. |
| **M3 — Canonical TCP Protocol** | ✅ Complete | HELLO/HELLO_ACK, PING/PONG, SHUTDOWN lifecycle over canonical MCUB framing. |
| **M4 — Fabric Guest Control Integration** | ✅ Complete | Fabric mod connects, completes the handshake, and validates START_STREAM mapping identity. |
| **M5 — Shared-Memory Framebuffer** | ✅ Complete | Canonical Java/C# triple buffer, START_STREAM identity, and asynchronous BGRA8 PBO capture are implemented and runtime-verified in Minecraft 1.21.1 at multiple resolutions, including reconnect. |

**Repository integrity:** The omitted host protocol sources were restored in commit `08535a2`; CI passed.

### M5 Implementation Status

Architecture B is the approved implementation: MCUB TCP control plane, file-backed shared-memory
triple buffer, and Minecraft framebuffer producer / Unity consumer. Existing START_STREAM ID 6
advertises the Linux mapping path, control session ID, and 128-bit generation.

The Java/C# shared-buffer implementations use canonical 128-byte headers, BGRA8 payloads,
generation validation, atomic ownership, sequence numbers, checksum, latest-frame selection, and
stale-frame draining. The Fabric guest reads the main framebuffer through three OpenGL pixel-pack
buffers and GPU fences, then publishes completed frames on a worker thread.

The capture code builds and Java tests pass, and it has now been exercised in the actual Minecraft
1.21.1 Fabric client. Runtime evidence includes 267 published frames at 1366x700 and an earlier
854x480 run, with BGRA8 metadata, payload lengths, checksums, triple-slot reuse, and continuous
sequence progression verified by a loopback consumer. A separate reconnect run published frames
before and after a simulated host disconnect using fresh mappings/generations. Unity host presentation
is Milestone 6; 3840x2160 GPU throughput is not claimed on this development display.

---

## Validation Notes

* The M3 TCP protocol and the M4 Fabric guest control integration are validated. M3 exercised native/Linux ↔ Wine Mono sockets, fragmented and coalesced frames, malformed/oversized/truncated frames, disconnect, and cleanup.
* Production Java ↔ C# shared-buffer publication/readback has been exercised in both directions against the same file mapping. A separate cross-process stress run passed 2,000 frames in each direction with metadata/checksum/payload validation; the latest-frame consumer observed 921 and 747 frames respectively because older READY frames are intentionally superseded.
* **Java ↔ C# shared-memory ordering has only been empirically validated on the target Wine Mono / Java 21 environment.** This is **not** a portable formal Java/.NET happens-before guarantee.
* START_STREAM guest tests validate the advertised session ID and generation before exposing the mapping. A live Minecraft 1.21.1 run published 267 frames at 1366x700; an earlier run published 33+ frames at 854x480. A loopback reconnect run published 9 frames on each of two fresh mappings after a simulated host disconnect. OpenGL PBO capture and live GPU readback are runtime-verified; Unity/ULTRAKILL presentation remains Milestone 6.

M3 validation: run `KEEP_LOGS=1 tools/tcp_protocol_validation/run.sh`. This suite exercises actual native/Linux ↔ Wine Mono sockets and shared mappings. `tools/tcp_control_validation/` is a legacy private-protocol experiment and is not canonical MCUB validation.

---

## Documentation

* `docs/environment.md` — audited host/guest environment and verified findings.
* `docs/architecture.md` — system architecture and inter-process pipeline.
* `docs/protocol.md` — canonical MCUB wire protocol and validation rules.
* `docs/roadmap.md` — milestone tracking and implementation status.
* `docs/milestone3_architecture_analysis.md` — analysis of synchronization design candidates.
