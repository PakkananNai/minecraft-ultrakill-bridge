# Milestone 10 — Entity Synchronization

## Status

**PASS — implementation, automated tests, and live entity-world verification completed.**

M10 runtime verification was completed in the existing `New World` save. AGENTS.md completion gates are satisfied with concrete host/guest runtime evidence.

## Implementation

### Guest

- Added GuestEntityBridge.
- Tracks entities within 64 blocks of the local player.
- Excludes the local player.
- Sends ENTITY_UPDATE for newly visible entities and meaningful state changes.
- Sends ENTITY_REMOVE when an entity leaves the tracked set or world.
- Clears tracking while disconnected and forces a fresh snapshot after reconnect.
- Sends registry type ID, position, rotation, velocity, and on-ground state.

### Entity interaction events

Added EntityInteractionMixin around Minecraft's normal ClientPlayerInteractionManager paths:
- attackEntity → ENTITY_INTERACTION type 2.
- interactEntity → ENTITY_INTERACTION type 1.
- interactEntityAtLocation → ENTITY_INTERACTION type 1 with hit position.

These are observational events. Minecraft's own interaction logic remains authoritative.

### Host

- Added EntityBridge.
- Host TCP dispatch validates and receives ENTITY_UPDATE, ENTITY_REMOVE, and ENTITY_INTERACTION.
- Entity state is stored as lightweight metadata only.
- No Unity entity recreation was introduced; this avoids speculative M10 architecture.

### Protocol

Added canonical ENTITY_INTERACTION = 17. Entity update/remove/interaction payloads are documented in docs/protocol.md.

## Additional issue found and fixed

The Prism runtime instance was using Fabric Loader 0.19.3 while the Gradle dependency declared 0.16.5. This was a real build/runtime version mismatch. The Gradle dependency is now aligned to 0.19.3.

## Verification

### VERIFIED

- Guest clean build: PASS.
- Guest JUnit suite with Minecraft runtime stopped: 16 tests PASS.
- New entity message serialization tests: PASS.
- Host build: PASS.
- Canonical message type 17 is accepted by the host parser.
- Live Minecraft guest loaded the M10 mixin configuration without a startup crash.
- Live guest ↔ host TCP session and framebuffer handshake still worked after the M10 changes.

### VERIFIED

Live runtime evidence now covers all M10 gates:
1. Real entity discovery produced `M10_ENTITY_UPDATE` messages with real Minecraft entity IDs/types/positions.
2. Spawn and removal were observed (`spawned=true` plus `M10_ENTITY_REMOVE`).
3. Moving entities produced repeated position/velocity/state updates.
4. A real Minecraft attack path produced `M10_ENTITY_INTERACTION` type `2` for entity id `3`.
5. After host disconnect/reconnect, session `3641842656` produced a fresh `spawned=true` snapshot for multiple entities.

Additional runtime evidence: the guest reconnected to the restarted host, validated the new shared-framebuffer identity, and continued M8/M9 traffic after reconnect.

## Scope decision

Skipped Unity-side entity GameObjects/renderers for M10. Add them only when a later milestone actually requires host-side entity recreation.

## Completion decision

M10 is PASS. No M11 work was started before the M10 runtime gates passed.

The next milestone is M11 — Damage & Gameplay Integration.
