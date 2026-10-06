# Milestone 11 — Damage & Gameplay Integration

## Status

**IN PROGRESS — automated implementation and protocol gates pass; live gameplay verification remains blocked.**

## Implementation

### Guest

- Added canonical DAMAGE_EVENT message support.
- Added LivingEntityDamageMixin around Minecraft normal damage path.
- Sends target ID, attacker ID, damage amount, current health, max health, and source type.
- Existing health snapshots use the same message family without bypassing Minecraft authority.

### Host

- Added DamageEventReceived dispatch.
- EntityBridge.OnDamage updates lightweight entity health state and emits diagnostic evidence.
- No speculative Unity damage/health system was added before the live gate is verified.

## Automated verification

- Guest Gradle test suite: PASS.
- Host build: PASS.
- git diff --check: PASS.
- TCP protocol validation: PASS.
  - cross-language fixtures
  - fragmented/coalesced packets
  - malformed packets
  - disconnect/idle
  - managed/native bidirectional validation
  - shared-memory lifecycle
  - process cleanup
- Canonical TCP header remains 16 bytes.

Latest TCP evidence:
tools/tcp_protocol_validation/logs/20261006T193157Z-74410/

## Remaining gate

Live Minecraft to host to ULTRAKILL damage verification is still required. The current session cannot launch the graphical ULTRAKILL runtime because no display driver/session is available (XDG_RUNTIME_DIR/Wine window creation failure).

M11 must not be marked PASS until a real gameplay damage event is observed end-to-end.
