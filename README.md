# Minecraft × ULTRAKILL Bridge

A bridge architecture allowing **Minecraft Java Edition** to run and behave as an integrated, playable world inside **ULTRAKILL**.

---

## High-Level Vision

* **Host Game:** ULTRAKILL (Unity 2022.3.29f1 Mono x64 running through Wine 11.0 on Ubuntu 26.04 LTS).
* **Guest Game:** Minecraft Java Edition 1.21.1 (Fabric Loader 0.19.3, Java 21).
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

* **TCP control plane (loopback `127.0.0.1:47653`):** Session negotiation, liveness, and structured control messages framed with the canonical MCUB header. The current endpoint handles `HELLO`/`HELLO_ACK`, `PING`/`PONG`, `ERROR`, and one-way `SHUTDOWN`. Other MCUB types are defined but not yet application-handled.
* **Shared-memory data plane:** Frame bytes and producer/consumer synchronization state live in a file-backed mapping (e.g. under `/tmp/`). This plane carries the actual framebuffer payload; only compact metadata/control events cross TCP.
* **Framebuffer producer/consumer:** The guest produces frames and the host consumes them, coordinated by shared-memory buffer state.

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
| **M4 — Fabric Guest Control Integration** | ✅ Complete | Fabric mod connects to the control channel and completes the handshake. |
| **M5 — Shared-Memory Framebuffer** | ⛔ Not implemented | Design/architecture still pending final approval. |

**Repository integrity:** The omitted host protocol sources were restored in commit `08535a2`; CI passed.

### M5 Design Pending Approval

Architecture B (TCP control plane → shared-memory synchronization/data plane → framebuffer producer/consumer) is the proposed direction for M5, but the design has **not** been finalized and no framebuffer integration has been implemented. There is currently no production shared-memory framebuffer ring, no triple-buffered slot ownership, and no host rendering of Minecraft frames. No framebuffer work should be assumed complete.

---

## Validation Notes

* The M3 TCP protocol and the M4 Fabric guest control integration are validated. M3 exercised native/Linux ↔ Wine Mono sockets, fragmented and coalesced frames, malformed/oversized/truncated frames, disconnect, and cleanup.
* File-backed shared-memory access has been demonstrated in both directions in proof-of-concept tooling.
* **Java ↔ C# shared-memory ordering has only been empirically validated in the POC.** It is **not** claimed as a portable formal Java/.NET happens-before guarantee; cross-runtime memory-order composition remains unproven.
* The shared-memory mapping helper created during M3 performs creation/opening, size validation, and cleanup only. It does not define framebuffer slots or ownership.

M3 validation: run `KEEP_LOGS=1 tools/tcp_protocol_validation/run.sh`. This suite exercises actual native/Linux ↔ Wine Mono sockets and shared mappings. `tools/tcp_control_validation/` is a legacy private-protocol experiment and is not canonical MCUB validation.

---

## Documentation

* `docs/environment.md` — audited host/guest environment and verified findings.
* `docs/architecture.md` — system architecture and inter-process pipeline.
* `docs/protocol.md` — canonical MCUB wire protocol and validation rules.
* `docs/roadmap.md` — milestone tracking and implementation status.
* `docs/milestone3_architecture_analysis.md` — analysis of synchronization design candidates.
