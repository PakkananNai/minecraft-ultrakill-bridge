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
├── README.md                   # Project overview and quickstart
├── docs/                       # Project documentation
│   ├── environment.md          # Environment audit & verified findings
│   ├── architecture.md         # System architecture & inter-process pipeline
│   └── roadmap.md              # Milestone tracking & implementation status
├── shared/                     # Shared IPC protocol and serialization
├── host/                       # ULTRAKILL host plugin (BepInEx 6 Unity Mono)
│   └── MinecraftBridge/
├── guest/                      # Minecraft Fabric mod (Java 21)
│   └── MinecraftFabricMod/
├── native/                     # Optional native helper libraries (POSIX / Win32)
├── tests/                      # Unit & integration tests
└── tools/                      # Build and verification scripts
```

---

## Current Status

* **Milestone 0 (Environment Inspection):** Completed. Environment audited and verified.
* **Milestone 1 (Minimal Host Plugin):** Completed.
* **Milestone 2 (Shared Protocol):** Completed; canonical C# and Java serializers use the fixed 16-byte MCUB header.
* **Milestone 3 (Interprocess Communication):** Implemented and validated for canonical TCP control lifecycle and file-backed mapping management. Framebuffer ownership/recovery is intentionally deferred.

Milestone 3 validation: run `KEEP_LOGS=1 tools/tcp_protocol_validation/run.sh`. The new suite exercises actual native/Linux ↔ Wine Mono sockets and shared mappings. `tools/tcp_control_validation/` is a legacy private-protocol experiment and is not canonical MCUB validation.
