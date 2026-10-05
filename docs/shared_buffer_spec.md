# M5 Canonical Shared Buffer Specification (Proposed)

**Document version:** 1.0.0 (proposal)
**Status:** CANONICAL v1.0 — implemented and runtime-validated for Milestone 5
**Scope:** Shared-memory framebuffer data plane for the Minecraft × ULTRAKILL Bridge
**Protocol impact:** NONE. This document does not change the canonical MCUB TCP protocol.

> This document is the canonical M5 data-plane specification. The production Java/C# shared-buffer
> implementation and Minecraft 1.21.1 capture path conform to this layout. The focused proof-of-concept
> harnesses remain validation evidence only; they are not alternative production layouts.

---

## A. Architecture

Architecture **B** is selected:

```
TCP control plane
        ↓
shared-memory synchronization / data plane
        ↓
framebuffer producer / consumer
```

Explicit statements:

- **TCP does not own shared-memory slots.** The control plane negotiates sessions, liveness,
  and compact control messages only.
- **Shared-memory ownership is independent of TCP messages.** Slot ownership is decided solely
  by the atomic slot `state` word in the mapping.
- **`GRANT`, `READY`, `RELEASE`, and `RESET` are NOT TCP messages.** They are data-plane
  ownership/synchronization concepts. They are never assigned a `MessageType` and are never
  serialized onto the control channel.
- **The canonical TCP protocol remains unchanged.** The 16-byte little-endian MCUB header,
  the message type enumeration, and all payload layouts are untouched.
- **The production TCP endpoint remains `127.0.0.1:47653`.**

The mapping header carries the accepted TCP `SessionId` as *metadata only*. `SessionId` is
not a shared-memory ownership mechanism and is not sufficient shared-memory fencing (see J, I).

---

## B. Production Target

| Property | Value |
| :--- | :--- |
| Maximum resolution | 3840 × 2160 |
| Pixel format | BGRA8 |
| Bytes per pixel | 4 |
| Slots | 3 (triple buffering) |
| Metadata endianness | Little-endian |
| Transport | File-backed shared mapping |
| Slot capacity policy | **Fixed maximum-capacity slots** |

**Fixed-capacity rule (normative).** Every slot is always sized for the maximum
`3840 × 2160` BGRA8 frame, regardless of the current resolution. A frame smaller than the
maximum uses the *same* slot capacity and records its actual `width`, `height`, `stride`, and
`payload_length` in the slot header. There are **no per-resolution mapping sizes**. A
resolution change therefore **never resizes the mapping** (see L).

The abandoned 196,864-byte and 8,192-byte proof-of-concept geometries are **not** the
production geometry and must not be reused.

**Roles (normative for M5).** For M5 framebuffer streaming, **Minecraft/Fabric is the framebuffer
producer** and **ULTRAKILL/Unity is the framebuffer consumer**. The shared-buffer mechanism itself
remains role-generic: the producer/consumer rules below are defined generically and applied to
these roles.

---

## C. Canonical Mapping Layout

**Normative constants:**

| Constant | Value |
| :--- | :--- |
| `SMEM_MAGIC` | `0x5342434D` (`"MCBS"` when stored little-endian) |
| `SMEM_LAYOUT_VERSION` | `1` |
| `MAPPING_HEADER_SIZE` | `128` bytes |
| `SLOT_HEADER_SIZE` | `128` bytes |
| `SLOT_COUNT` | `3` |
| `SLOT_CAPACITY` | `33,177,600` bytes |
| `SLOT_STRIDE` | `33,177,728` bytes |
| `MAPPING_TOTAL_SIZE` | `99,533,312` bytes |

Layout order: `[mapping header][slot 0][slot 1][slot 2]`, where each slot is
`[slot header][slot payload]`.

Slot *i* base offset = `MAPPING_HEADER_SIZE + i * SLOT_STRIDE`.
Slot *i* payload offset = slot base offset + `SLOT_HEADER_SIZE`.

### C.1 Mapping Header — 128 bytes, at mapping offset 0

| Abs offset | Size | Type | Align | Endian | Field | Meaning | Writer | Reader | Atomic? | Init |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| +0 | 4 | uint32 | 4 | LE | `magic` | `SMEM_MAGIC` | host (create) | both | no | `0x5342434D` |
| +4 | 2 | uint16 | 2 | LE | `layout_version` | `SMEM_LAYOUT_VERSION` | host | both | no | `1` |
| +6 | 2 | uint16 | 2 | LE | `header_size` | `MAPPING_HEADER_SIZE` | host | both | no | `128` |
| +8 | 4 | uint32 | 4 | LE | `slot_count` | `SLOT_COUNT` | host | both | no | `3` |
| +12 | 4 | uint32 | 4 | LE | `slot_stride` | `SLOT_STRIDE` | host | both | no | `33177728` |
| +16 | 4 | uint32 | 4 | LE | `slot_capacity` | `SLOT_CAPACITY` | host | both | no | `33177600` |
| +20 | 4 | uint32 | 4 | LE | `flags` | mapping capability bits (C.4) | host | both | no | see C.4 |
| +24 | 4 | uint32 | 4 | LE | `session_id` | accepted TCP `SessionId` (metadata) | host | both | no | from HELLO_ACK |
| +28 | 4 | uint32 | 4 | LE | `reserved0` | alignment pad to 8-byte boundary; **purpose: keep the 128-bit generation naturally aligned; must be 0** | host | both | no | `0` |
| +32 | 8 | uint64 | 8 | LE | `generation_hi` | high 64 bits of the 128-bit generation | host | both | no | random |
| +40 | 8 | uint64 | 8 | LE | `generation_lo` | low 64 bits of the 128-bit generation | host | both | no | random |
| +48 | 8 | uint64 | 8 | LE | `creation_timestamp_ns` | host **diagnostic** timestamp at creation (host clock domain); follows the same clock-domain rules as `timestamp_ns` (see C.2). If recorded on a monotonic clock it MUST NOT be used for cross-process or cross-reboot cleanup-age calculations (K) | host | host | no | host |
| +56 | 8 | uint64 | 8 | LE | `reserved1` | reserved; **purpose: aligns the extension region to +64; must be 0** | host | both | no | `0` |
| +64 | 64 | byte[64] | — | — | `reserved_extension` | reserved for future compatible fields without relocating slot 0; **must be zero-initialized** | host | both | no | `0` |

`reserved0`, `reserved1`, and `reserved_extension` exist so that the mapping header is exactly
two 64-byte cache lines and all multi-byte fields are naturally aligned. Receivers **must not**
interpret reserved bytes; hosts **must** zero them at creation. Reserved bytes are never
repurposed in layout version 1.

### C.2 Slot Header — 128 bytes, at each slot base

| Slot-rel offset | Size | Type | Align | Endian | Field | Meaning | Writer | Reader | Atomic? | Init |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| +0 | 4 | uint32 | 4 | LE | `state` | slot state (G) | producer/consumer (owner only) | both (observe) | **yes** | `FREE (0)` |
| +4 | 4 | uint32 | 4 | LE | `reserved0` | alignment pad; **purpose: 8-byte alignment of `sequence`; must be 0** | host | both | no | `0` |
| +8 | 8 | uint64 | 8 | LE | `sequence` | **global mapping/session frame sequence, shared across all 3 slots (not per-slot)**; starts at 1 for the first published frame; +1 per successfully published frame; see note below | producer | consumer | no | `0` |
| +16 | 8 | uint64 | 8 | LE | `generation_hi` | copy of mapping `generation_hi` | producer | consumer | no | `0` |
| +24 | 8 | uint64 | 8 | LE | `generation_lo` | copy of mapping `generation_lo` | producer | consumer | no | `0` |
| +32 | 4 | uint32 | 4 | LE | `width` | frame width in pixels (≤ 3840) | producer | consumer | no | `0` |
| +36 | 4 | uint32 | 4 | LE | `height` | frame height in pixels (≤ 2160) | producer | consumer | no | `0` |
| +40 | 4 | uint32 | 4 | LE | `stride` | row pitch in bytes (≥ `width × 4`) | producer | consumer | no | `0` |
| +44 | 4 | uint32 | 4 | LE | `pixel_format` | `2 = BGRA8` (`1 = RGBA8` defined but not used by M5) | producer | consumer | no | `0` |
| +48 | 4 | uint32 | 4 | LE | `payload_length` | valid byte count in payload (≤ `SLOT_CAPACITY`) | producer | consumer | no | `0` |
| +52 | 4 | uint32 | 4 | LE | `reserved1` | alignment pad; **purpose: 8-byte alignment of `timestamp_ns`; must be 0** | host | both | no | `0` |
| +56 | 8 | uint64 | 8 | LE | `timestamp_ns` | producer **monotonic-clock** timestamp (not Unix epoch; producer clock domain only); see note below | producer | consumer | no | `0` |
| +64 | 8 | uint64 | 8 | LE | `checksum` | 64-bit FNV-1a over the first `payload_length` payload bytes when flags bit0 = 1 | producer | consumer | no | `0` |
| +72 | 56 | byte[56] | — | — | `reserved_extension` | reserved; **must be zero-initialized**; keeps slot header at exactly two cache lines | host | both | no | `0` |

**`sequence` semantics (normative).** `sequence` is a **global** frame sequence for the mapping/session, **shared across all 3 slots** — it is **not** a per-slot counter. It starts at **1 for the first successfully published frame** and increments by **exactly 1 for every successfully published frame**. A **dropped frame does not consume a published sequence number**; therefore a discontinuity in observed `sequence` values indicates a lost/failed publication or a duplicate. The consumer uses `sequence` to establish frame ordering, to detect gaps and duplicates, and as the ordering key for READY-slot selection (G). Consumer supersession of older `READY` frames (G) is **not** a producer frame-drop event: a superseded frame was already successfully published, so supersession leaves no `sequence` gap and MUST NOT be counted or reported as a producer drop.

**`timestamp_ns` semantics (normative).** `timestamp_ns` is a **producer monotonic-clock** timestamp. It is **not** Unix epoch time, it is meaningful **only within the producer's clock domain**, and it **must not** be numerically compared against the consumer's clock. It is used for diagnostics and latency measurement only.

### C.3 Slot Payload

| Slot-rel offset | Size | Type | Meaning |
| :--- | :--- | :--- | :--- |
| +128 | `SLOT_CAPACITY` = 33,177,600 | byte[] | BGRA8 pixel bytes, `payload_length` valid, rest undefined |

The payload is **BGRA8 byte order** (B at lowest address). Pixel bytes are **never
endian-swapped**. The Minecraft OpenGL producer stores rows in `glReadPixels` order: the first
payload row is the framebuffer's bottom row (OpenGL lower-left origin). Consumers that use a
top-left image convention MUST invert the vertical texture coordinate or rows; the producer does
not perform a second full-frame flip.

### C.4 `flags` bit definitions (mapping header +20)

| Bit | Name | Meaning |
| :--- | :--- | :--- |
| 0 | `CHECKSUM_ENABLED` | When set, producers compute `checksum`; consumers validate it. Host sets it at creation. |
| 1–31 | reserved | **must be 0**. |

Default proposed value: `CHECKSUM_ENABLED` set (`flags = 1`). If a future profile disables it,
`checksum` may be 0 and consumers must skip validation; this must be an explicit, negotiated
mapping flag, never an implicit convention.

**`checksum` scope (normative).** The `checksum` field does **not** participate in ownership, does
**not** participate in publication ordering, and does **not** participate in generation fencing.
It is integrity/debug validation only. Performance validation must measure its cost before
production enables it by default.

---

## D. Arithmetic

**Payload / capacity:**

```
3840 × 2160 × 4 bytes
  = 8,294,400 pixels × 4
  = 33,177,600 bytes per slot payload
SLOT_CAPACITY = 33,177,600 bytes
```

**Stride and totals:**

```
SLOT_HEADER_SIZE   = 128
SLOT_STRIDE        = SLOT_HEADER_SIZE + SLOT_CAPACITY
                   = 128 + 33,177,600
                   = 33,177,728 bytes

MAPPING_HEADER_SIZE = 128
MAPPING_TOTAL_SIZE  = MAPPING_HEADER_SIZE + SLOT_COUNT × SLOT_STRIDE
                    = 128 + 3 × 33,177,728
                    = 128 + 99,533,184
                    = 99,533,312 bytes
```

**Per-slot offsets:**

| Slot | Slot base | Header range | Payload start | Payload range (end exclusive) |
| :--- | :--- | :--- | :--- | :--- |
| 0 | 128 | [128, 256) | 256 | [256, 33,177,856) |
| 1 | 33,177,856 | [33,177,856, 33,177,984) | 33,177,984 | [33,177,984, 66,355,584) |
| 2 | 66,355,584 | [66,355,584, 66,355,712) | 66,355,712 | [66,355,712, 99,533,312) |

**Verification:**

- Slot 0 payload end `256 + 33,177,600 = 33,177,856` = slot 1 base. ✔
- Slot 1 payload end `33,177,984 + 33,177,600 = 66,355,584` = slot 2 base. ✔
- Slot 2 payload end `66,355,712 + 33,177,600 = 99,533,312` = `MAPPING_TOTAL_SIZE`. ✔
- No gaps, no overlaps; every byte of `[0, 99,533,312)` is accounted for.

---

## E. Alignment

| Item | Rule |
| :--- | :--- |
| Mapping header start | offset 0; 64-byte aligned (page/file start) |
| Mapping header size | 128 bytes (2 × 64-byte cache lines); all fields naturally aligned |
| Slot base | `128 + i × SLOT_STRIDE`; `SLOT_STRIDE` is a multiple of 128, so every slot base is 128-byte aligned |
| Slot header start | equal to slot base; 128-byte aligned |
| `state` field | offset +0 within slot header → 4-byte aligned **and** 8-byte aligned |
| 64-bit fields | all at 8-byte multiples within their header (`sequence` +8, generation +16/+24, `timestamp_ns` +56, `checksum` +64, mapping timestamp +48, generation +32/+40) |
| 128-bit generation | represented as **two `uint64` values** `generation_hi` (+32) and `generation_lo` (+40) in the mapping header; same split in each slot header at +16/+24 |
| Slot payload base | offset 256 / 33,177,984 / 66,355,712; all multiples of 128 |

**128-bit generation representation (normative).** The generation is a 128-bit value carried
as two little-endian `uint64` halves. It is **not** a native 128-bit atomic object. There is
**no 128-bit compare-and-swap**. Generation comparison is an ordinary consistency check over
two 64-bit reads, valid only after the relevant acquire/publication edge (H, I).

---

## F. Endianness

All multi-byte scalar metadata is **little-endian**.

| Type | Encoding |
| :--- | :--- |
| uint16 | 2 bytes, least-significant byte at lowest offset |
| uint32 | 4 bytes, least-significant byte at lowest offset |
| uint64 | 8 bytes, least-significant byte at lowest offset |
| generation halves | each half is a little-endian `uint64` (`generation_hi`, `generation_lo`) |
| pixel payload | BGRA8 byte order, **not endian-swapped** — interpreted as a byte sequence |

---

## G. State Machine

Canonical `state` values (`uint32`). The `state` word MUST be accessed as a single 32-bit atomic object; torn, half-width, or otherwise non-atomic `state` access is forbidden (H).

| Value | Name |
| :--- | :--- |
| 0 | `FREE` |

[executed on device: demo (6087f9ba-7d9b-4f79-a051-e5f6c7cfdde6)]| 1 | `WRITING` |
| 2 | `READY` |
| 3 | `READING` |

**Legal transitions (only these):**

| Transition | Owner | Mechanism |
| :--- | :--- | :--- |
| `FREE → WRITING` | producer | atomic compare-and-swap against `FREE` |
| `WRITING → READY` | producer | release publication store |
| `READY → READING` | consumer | acquire claim (observe `READY`, then CAS `READY → READING`) |
| `READING → FREE` | consumer | release store |

**Prohibited (normative):**

- Producer must **not** reclaim a slot in `READY`.
- Consumer must **not** reclaim a slot in `WRITING`.
- No third party may reset another owner's slot (no external `RESET` into the mapping).
- No timeout-based slot reclamation: a timeout never authorizes transition of a slot owned
  by another party.
- A slot is only ever transitioned by the owner defined above.

**Producer acquisition.** A producer scans all three slots and performs a CAS `FREE → WRITING`.
If no CAS succeeds it drops the new frame (N). The producer MUST NOT reclaim a slot in `READY`.

**Consumer: latest-frame selection (normative).** The framebuffer consumer is a *latest-frame
consumer*.

1. When one or more slots are `READY`, the consumer MUST observe each candidate `READY` state with
   **acquire semantics before reading that slot's `sequence` or frame metadata** for selection or
   §L validation. It MUST then scan all slots and select the `READY` slot whose `sequence` is the
   **greatest** among the candidates that pass the §L frame-metadata validation predicate and
   belong to the current mapping generation.
2. `sequence` is the ordering key for READY selection; a **higher `sequence` is newer**. After
   the acquire observation required by item 1, the comparison MUST be performed only over `READY`
   slots whose metadata passes the §L frame-metadata validation predicate and whose generation
   matches the current mapping generation (I, L). Any `Generation` read performed
   during selection is only a **non-authoritative filter** over immutable metadata (I.9); the
   **binding** generation validation is the post-acquire consistency check in item 5 (G.5, I.3).
3. The consumer MUST claim the selected slot using a **CAS `READY → READING`** (acquire). This
   `READY → READING` transition is a **legal ownership-acquisition transition authorized by the
   consumer state-machine ownership rule**; it is **NOT** authorized by, and does not depend on,
   the slot `Generation` value.
4. If the CAS fails, the consumer MUST **rescan** and retry. **A failed CAS MUST NOT cause any
   direct state modification.**
5. After successfully claiming the newest `READY` slot, the consumer MUST perform a **post-acquire
   consistency/fencing check**: it MUST read and validate the slot `Generation` and the frame
   metadata (I, L, M) **before reading the payload or presenting** the frame. The claim in item 3
   does not depend on this check; the check occurs only after the slot is already owned.
6. If the post-acquire `Generation` check fails, the consumer MUST: not present the frame and MUST
   NOT read or use the payload; MUST NOT modify the payload or metadata; and MUST release the slot
   it already owns using the normal consumer release **`READING → FREE`**. That release is an
   **ownership cleanup** of a slot the consumer already owns — it is **NOT** "reclaiming" a slot
   belonging to another owner. No other `state` transition is permitted, and no timeout, reset, or
   third-party reclaim is involved. The consumer then treats the mapping as stale (I.4) and stops
   using it.
7. The same post-acquire disposition applies to **invalid frame metadata** (L): the consumer MUST
   NOT present or read the invalid payload, MUST NOT repair or rewrite metadata or payload, and
   MUST release the already-owned `READING` slot to `FREE`, then continue/rescan as appropriate.
8. Older `READY` slots are **stale/superseded** frames. During its selection/drain pass the
   consumer MUST drain superseded `READY` slots by claiming `READY → READING` and then immediately
   releasing `READING → FREE` **without presenting** them. A superseded `READY` slot MUST NOT be
   left permanently `READY` (see O.16).
9. **Only the consumer** may perform the `READY → READING → FREE` drain operation. A drained slot
   follows the same legal transitions as a normal consumer acquisition/release.
10. The producer MUST NOT reclaim a `READY` slot (superseded or otherwise).
11. If no slot is `READY`, the consumer MUST continue presenting its previously accepted frame if
    one exists; if it has never accepted a frame it presents nothing until a slot becomes `READY`.
12. No timeout-based reclaim is allowed; a timeout MUST NOT transition a slot owned by another
    party.

---

## H. Memory Ordering

Four distinct concerns are documented separately:

1. **State atomicity** — the `state` word is the *only* atomic read-modify-write object. It is
   a naturally aligned 32-bit value and MUST be accessed as **one 32-bit atomic object**; torn,
   half-width, or otherwise non-atomic `state` access is forbidden. All ownership changes go
   through atomic operations on it.
2. **Publication ordering** — `WRITING → READY` is a **release** publication. All payload and
   metadata stores that precede it must be visible to any observer that acquires `READY`.
3. **Payload visibility** — the consumer must observe `READY` with **acquire** semantics
   *before* reading metadata or payload. The critical edge is:
   ```
   producer: write metadata + payload (ordinary stores)
             WRITING → READY                     (release)
   consumer: observe READY                        (acquire)
             read metadata + payload
   ```
4. **Generation validation** — an ordinary consistency check performed after the acquire
   edge, not an atomic primitive (I).

**Intended API contract:**

| Runtime | Publication (`→READY`, `→FREE`) | Acquisition (`READY→READING`) | Polling |
| :--- | :--- | :--- | :--- |
| C# (.NET / Wine Mono) | `Interlocked.Exchange` | `Interlocked.CompareExchange` (after observing `READY`) | `Volatile.Read` |
| Java 21 | `VarHandle.setRelease` | `VarHandle.compareAndSet` (after `getAcquire` observation) | `VarHandle.getAcquire` |

`Interlocked` operations provide full-fence semantics on .NET; `Volatile.Read` provides acquire
semantics. Java's `setRelease`/`getAcquire` map to release/acquire order; `compareAndSet` is a
full fence. Choice of a full-fence CAS for the `READY → READING` claim is intentional and
conservative.

**Evidence boundary (normative):** the above composition has been **empirically validated only**
on the exact target environment (Java 21; Wine Mono `mcs` 6.13.0.0; Wine 11.0; Ubuntu 26.04;
x86_64). It is **not** claimed as a portable formal Java/.NET cross-process happens-before
guarantee, and it is **not** claimed that the Wine Mono atomic operations are lock-free.
Results on other runtimes, compiler versions, or architectures do not follow from these
observations.

---

## I. Generation Fencing

Each mapping has:

- the accepted TCP `SessionId` (`session_id`, mapping header +24), and
- a fresh cryptographically random **128-bit `Generation`** (`generation_hi`/`generation_lo`,
  mapping header +32/+40).

Each slot carries a **copy** of the `Generation` in its slot header (`generation_hi`/`generation_lo`
at +16/+24).

**Rules (normative):**

1. The host generates the 128-bit `Generation` from a cryptographically secure source (e.g.
   `SecureRandom`/`RandomNumberGenerator`) when creating a mapping, and never reuses it.
2. The **producer validates the mapping generation** against its own expected generation before
   using the mapping, and copies `generation_hi`/`generation_lo` into the slot header as part of
   the pre-publication writes that precede the `WRITING → READY` release.
3. The **consumer validates the slot generation** by comparing the slot header copies against
   the mapping header generation **after** acquiring the slot (`READY → READING`) and **before**
   reading payload. The `READY → READING` acquisition is authorized by the consumer state-machine
   ownership rule, **not** by the `Generation`; the generation comparison is a **post-acquire
   consistency gate** (G.3, G.5–G.7).
4. **Stale-generation data is rejected.** If either side observes a generation that does not
   match its expected mapping generation, it must treat the mapping as belonging to another
   session, stop using it, and discard the observation.
5. **Stale observers MUST NOT take or modify slots they do not own.** On a generation mismatch an
   observer MUST NOT take, reset, or write the `state`, metadata, or payload of a slot it does
   **not** own — not even to "reset" it. A slot the observer has **already legitimately acquired**
   (`READY → READING`) **is owned by that observer** and MAY be released per G.6; that release is
   an ownership cleanup, not a transfer of another owner's slot.
6. **Generation mismatch never authorizes reclaim.** A mismatch is a fencing signal, not an
   ownership token. The only way to acquire a slot remains CAS `FREE → WRITING` by the producer.
7. **`SessionId` alone is insufficient fencing.** `session_id` is informational; correctness of
   fencing rests on the 128-bit `Generation`.
8. **Generation scope (normative).** Each `Generation` belongs to **exactly one mapping file
   object**. An old `Generation` does **not** grant access to a new mapping. A process must only
   open or use a mapping when the advertised **mapping identity and its expected `Generation`
   match**. `Generation` is an **identity/fencing** mechanism, **not** an access-control or
   security mechanism. Old mapping handles remain associated with the **old** file object, and
   **reconnect never mutates an old mapping into the new generation**.
9. **Generation is immutable.** A mapping's `generation_hi`/`generation_lo` are written by the
   host once, during mapping initialization, and **MUST NOT be changed in place** for the lifetime
   of the mapping file object. The two `uint64` fields are ordinary immutable metadata, **not** a
   128-bit atomic object; they MUST be read as two `uint64` values, not as one 128-bit atomic
   object, and never as a 128-bit CAS operand.
10. **Expected `Generation` establishment.** Before accessing the data plane, a peer MUST:
    (a) identify the intended mapping/file object; (b) obtain the advertised `Generation` via the
    approved advertisement mechanism (§P); (c) store that value as its **expected Generation**; and
    (d) validate that the mapping header `Generation` matches the expected Generation. The expected
    Generation used thereafter by the producer/consumer is exactly the immutable value obtained in
    this mapping-establishment step.
11. **Initialization precedes access.** The host MUST initialize the mapping header and **all
    slots** before the mapping is advertised to the peer. Because `Generation` is immutable after
    initialization (rule 9) and the peer accesses the mapping only after initialization and
    advertisement, reading `generation_hi`/`generation_lo` as two `uint64` values is a
    consistency check, **not** a concurrent update operation.
12. **Stale/mismatched `Generation` MUST NOT authorize reclaim of another owner's slot.** A
    missing, stale, or mismatched `Generation` is a fencing signal only (consistent with rule 6).
    Specifically: a mismatch does **not** allow an observer to take, reset, or reclaim a slot it
    does **not** own, and does **not** authorize any **unowned** `state` transition. However, a
    mismatch does **not** prohibit the **legitimate owner** from releasing a slot it has already
    successfully acquired: the post-acquire `READY → READING` claim is authorized by the consumer
    state-machine ownership rule (G.3), not by the `Generation`, and a subsequent mismatch is
    handled by releasing the already-owned slot `READING → FREE` (an ownership cleanup, not a
    reclaim).
13. **Why the mismatch path is normally unreachable.** Mapping `Generation` is immutable
    (rule 9); the mapping header and all slots are initialized before advertisement (rule 11);
    the peer establishes its expected `Generation` before any data-plane access (rule 10, J.8);
    and reconnect creates a new mapping/file object with a new `Generation` (K). A current, valid
    mapping should therefore always present a matching slot `Generation`, but the post-acquire
    validation (G.5–G.7) remains a defensive consistency gate.

**When generation values are written/validated:**

| Moment | Action |
| :--- | :--- |
| Mapping creation (host) | choose random 128-bit generation; write mapping header |
| Slot initialization (host) | zero slot header, set `state = FREE` |
| Before streaming, guest open | read mapping header; validate `magic`, version, sizes, `session_id`, and that the generation matches the value it expects |
| Producer, before each publication | (re)validate mapping generation; copy generation halves into the slot being written (before release) |
| Mapping advertisement / establishment (pending §P) | peer identifies the mapping/file object and stores the advertised `Generation` as its expected Generation |
| Consumer, after acquiring a slot, before reading payload | compare slot generation halves to mapping header; reject on mismatch without modifying the slot |

---

## J. Mapping Lifecycle

1. **Host accepts a TCP session.** The existing M3 handshake negotiates HELLO/HELLO_ACK and
   allocates a nonzero `SessionId`. No protocol change.
2. **Host creates a new mapping / file object** — a *new* file object is created for the session
   (never reuse or resize an existing mapping in place).
3. **Host generates a fresh `Generation`.** A fresh cryptographically random 128-bit `Generation`
   is generated for this mapping and is immutable after initialization (I.9).
4. **Host writes the mapping header**, including the `Generation` (`generation_hi`/`generation_lo`),
   plus `magic`, `layout_version`, `header_size`, `slot_count`, `slot_stride`, `slot_capacity`,
   `flags`, `session_id`, `creation_timestamp_ns`, and zeroed reserved bytes.
5. **Host initializes all slots `FREE`** (state = 0) and zeroes each slot header. The mapping
   header and **all slots** are fully initialized **before** the mapping is advertised to the peer.
6. **Host advertises the mapping identity** — path/file object identity, `session_id`, and
   `Generation` — to the guest. **This step depends on the mapping-advertisement mechanism that the
   canonical MCUB protocol does not yet define (§P).** No new `MessageType` may be invented. When
   that mechanism is approved, it MUST communicate enough information for the guest to identify
   the intended mapping/file object and to establish the advertised `Generation` as its expected
   Generation.
7. **Guest opens the mapping** and verifies it identifies the intended file object (O.14).
8. **Guest establishes and validates the expected `Generation`.** The guest MUST NOT access the
   framebuffer data plane until it has: identified the intended mapping/file object; obtained the
   advertised `Generation`; stored that value as its expected Generation; and validated that the
   mapping header `Generation` matches it (I.9–I.12).
9. **Streaming begins** — the producer claims slots via the state machine; the consumer selects
   the newest `READY` frame per G.

`Generation` is immutable after initialization and MUST NOT be changed in place (I.9). Reconnect
creates a new mapping/file object and a new `Generation` (K).

Proposed mapping identity/naming (subject to P): a per-session file object named so that
`session_id` and `generation` appear in the name, e.g.
`<shared_dir>/mcub_fb_<session_id>_<generation_hi_hex><generation_lo_hex>.shm`. The `<shared_dir>`
and its Wine/native path translation are an integration decision (P); the name is inert until a
compliant advertisement mechanism exists.

---

## K. Crash / Reconnect

**Detection**
- A TCP disconnect or receive timeout indicates the control connection ended, but **a timeout
  does not prove the peer process died**. Liveness is a control-plane concern.

**Fencing**
- Fencing is by the 128-bit `generation`. An observer that sees a stale/mismatched generation
  stops using the mapping and MUST NOT take, reset, modify, reclaim, or otherwise mutate any slot
  it does **not** own (I.5, I.12). If the observer had already legitimately acquired a slot
  (`READY → READING`) before detecting the mismatch, it **owns** that slot and MUST release it via
  the normal `READING → FREE` **ownership-cleanup** operation (G.6) — this is cleanup of an owned

[executed on device: demo (6087f9ba-7d9b-4f79-a051-e5f6c7cfdde6)]  slot, **not** reclamation of a stale/unowned slot — and MUST NOT modify that acquired slot's
  metadata or payload after the mismatch.
- Each `generation` belongs to exactly one mapping file object; an old `generation` never grants
  access to a new mapping, and reconnect never mutates an old mapping into the new generation
  (I.8).
- `generation` is immutable after initialization and MUST NOT be changed in place (I.9). A stale
  or mismatched `generation` MUST NOT authorize reclamation of a slot the observer does not own
  (I.12).

**Replacement**
- A producer that crashes while a slot is `WRITING` leaves the old mapping abandoned; the slot
  is **not** repaired in place.
- A consumer that crashes while a slot is `READING` leaves the old mapping abandoned; the slot
  is **not** repaired in place.
- Reconnect creates a **new mapping / file object** and a **new random generation**.
- The **old mapping cannot be reused**; no slot, index, or generation is carried across.
- **Stale generation cannot mutate the new mapping**: a surviving process holding an old
  generation descriptor is rejected by generation mismatch and never writes.

**Cleanup (explicit policy)**
- Abandoned mapping files are cleaned up by the **host only**, and only when it can establish
  that the owning session is closed (for example, host restart, or a mapping whose owning session
  is otherwise proven closed). `creation_timestamp_ns` is a **diagnostic** timestamp; if it is
  recorded on a monotonic clock it MUST NOT be used for cross-process or cross-reboot cleanup-age
  calculations.
- Cleanup **deletes the file object**; it does **not** reclaim or repair slots. File deletion and
  slot reclamation are separate concerns.
- The host **never** deletes a mapping it currently believes is in use.
- The guest never deletes mapping files.
- OS/tmpfs clearing the shared directory on reboot is acceptable and treated as cleanup.

**Separation of concerns (normative):** *detection* (control plane), *fencing* (generation),
*replacement* (new file object + new generation), and *cleanup* (host-side file deletion) are
distinct and must not be conflated. No timeout ever authorizes slot reclamation.

---

## L. Resolution Change

Because slot capacity is fixed at the maximum `3840 × 2160` BGRA8 size:

- A resolution change **does not resize the mapping** and does not create a new one.
- The producer updates per-frame `width`, `height`, `stride`, and `payload_length`.
- The consumer validates the following **before** reading payload, using **overflow-safe
  arithmetic evaluated in at least 64-bit width** (all products and sums MUST be computed as
  64-bit values before any bounds comparison):
  - `1 ≤ width ≤ 3840`
  - `1 ≤ height ≤ 2160`
  - `pixel_format == 2` (`BGRA8`)
  - `stride ≥ width × 4`
  - `stride ≤ SLOT_CAPACITY` and `payload_length ≤ SLOT_CAPACITY` (the declared row layout MUST fit
    within the slot capacity)
  - `payload_length ≥ required_bytes = (height − 1) × stride + width × 4` when the frame is densely
    packed to `payload_length`
- **Invalid metadata is rejected and the slot is released.** If any of the above fails, the
  consumer MUST NOT read the payload, MUST NOT repair or rewrite metadata or payload, and MUST
  release the already-owned `READING` slot via `READING → FREE` (G.7), then continue/rescan as
  appropriate.
- The maximum resolution remains `3840 × 2160`.

---

## M. Frame Metadata

Semantics of each slot-header frame field:

| Field | Meaning | Written before publication? | Read after acquisition? |
| :--- | :--- | :--- | :--- |
| `sequence` | Global mapping/session frame sequence, shared across all 3 slots (not per-slot); starts at 1, +1 per successfully published frame, dropped frames do not consume a number; **ordering key for READY selection (greatest valid sequence is newest)**; used to order frames and detect gaps/duplicates | yes | yes |
| `width` | Frame width in pixels (≤ 3840) | yes | yes |
| `height` | Frame height in pixels (≤ 2160) | yes | yes |
| `stride` | Row pitch in bytes (≥ `width × 4`) | yes | yes |
| `pixel_format` | `2 = BGRA8` (M5); `1 = RGBA8` defined but unused | yes | yes |
| `payload_length` | Valid payload bytes in the slot | yes | yes |
| `timestamp_ns` | Producer monotonic-clock timestamp; not Unix epoch; meaningful only in the producer clock domain; never compared to the consumer clock; diagnostics/latency only | yes | yes |
| `checksum` | 64-bit FNV-1a over `payload_length` payload bytes when `flags.CHECKSUM_ENABLED` | yes | yes |
| `generation_hi`/`generation_lo` | Fencing copy | yes | yes |

**Publication ordering:** all of the above metadata, plus the payload bytes, are written by the
producer **before** the release publication `WRITING → READY`. **All** of the above are read by
the consumer **after** the acquire claim `READY → READING`. No metadata field is written after
publication for a published frame. `state` is the only field mutated at/around the transitions.

**Supersession vs. drop.** Consumer supersession/drain of older `READY` frames (G) is **not** a
producer frame-drop event and MUST NOT be reported as one: a superseded frame was already
successfully published (it carries a valid `sequence`), whereas a producer-dropped frame is never
published and therefore leaves a `sequence` gap.

---

## N. Backpressure

**Chosen policy: drop-newest (producer-only).**

This section defines **producer** behavior only. Consumer-side handling of multiple `READY` slots
(latest-frame selection and draining superseded frames) is defined in **G**, not here.

When the producer cannot obtain a `FREE` slot (all slots are `WRITING`, `READY`, or `READING`):

- the producer **never blocks indefinitely**;
- the producer **drops the new frame** (it does not publish it);
- the producer **must not overwrite** a slot in `READY` or `READING`;
- the producer **must not reclaim another owner's slot** (including any `READY` slot, superseded
  or otherwise).

**Rationale.** The Minecraft render thread must never be blocked by IPC (prompt.md, AGENTS.md).
Blocking or overwriting a `READY`/`READING` slot would risk presenting a torn frame and would
violate the ownership invariants of G. Dropping the newest frame is the only producer policy that
keeps the render thread non-blocking while preserving every ownership rule. The consumer presents
the newest valid `READY` frame it has selected (G) and, if no `READY` slot is available, continues
presenting its previously accepted frame rather than freezing.

The consumer's **supersession/drain** of older `READY` frames (G) is a separate mechanism, not a
change to this producer policy. This policy may later be revisited only through an explicit
specification revision; it is not implied by this document.

---

## O. Validation Requirements and Extended Hardening

The following checks define the validation envelope for the canonical layout. The M5 exit criteria
used by the project are satisfied by the evidence recorded in `docs/milestone5_report.md` and
`docs/roadmap.md`. Items marked as extended hardening are deliberately retained as future stress,
crash-recovery, or portability work rather than being prerequisites for M5 completion.
All tests must be run against this exact layout.

**Current evidence:** Java unit tests pass for normal publication, latest-frame selection with
three simultaneous `READY` slots, stale-frame draining, reopen/cleanup, and invalid frame
footprints. A Wine Mono C# harness and Java harness have also passed both C#→Java and Java→C#
publication/readback against the same 99,533,312-byte mapping.

1. **Layout byte-offset validation** — assert every field of C.1/C.2/C.3 is at the documented
   offset with the documented size/alignment, on both runtimes.
2. **Mapping size validation** — assert `MAPPING_TOTAL_SIZE = 99,533,312`, `SLOT_STRIDE =
   33,177,728`, `SLOT_CAPACITY = 33,177,600`, and that a mismatched file size is rejected.
3. **Header initialization** — a freshly created mapping has the documented magic/version/sizes,
   all slots `FREE`, and all reserved bytes zero.
4. **3-slot normal stress** — millions of full-resolution frames in both directions with
   sequence and payload verification; assert the global `sequence` starts at 1, increments by
   exactly 1 per published frame, does not advance on dropped frames, and produces no gaps or
   duplicates when no frame is dropped; all slots reused and drained to `FREE`.
5. **Slow consumer / backpressure** — saturated ring with a deliberately slow consumer; assert
   no ownership violation, that producer drop-newest is honored without blocking, and that
   superseded `READY` slots are drained (see 16).
6. **Generation mismatch** — a stale-generation observation is rejected.
7. **Producer crash during `WRITING`** — old mapping abandoned; no in-place repair; recovery via
   new mapping/generation.
8. **Consumer crash during `READING`** — old mapping abandoned; no in-place repair.
9. **Reconnect / new mapping identity** — reconnect yields a new file object and a new random
   generation; no cross-generation frame is presented.
10. **Resolution changes** — smaller frames use the same slot capacity with correct
    width/height/stride/payload_length; no resize occurs.
11. **Malformed metadata** — bad magic/version/sizes/dimensions/stride/payload_length/format are
    rejected safely without modifying peer-owned slots.
12. **Payload bounds** — `payload_length ≤ SLOT_CAPACITY` and `stride ≥ width × 4` enforced;
    oversized/undersized rejected.
13. **Checksum / sequence validation** — when `CHECKSUM_ENABLED`, a corrupted payload is
    detected; the global `sequence` detects gaps/duplicates (a gap indicates a lost/failed
    publication, since dropped frames do not consume a sequence number). Checksum cost is
    measured before production default-enables it.
14. **Mapping identity verification** — device/inode (or equivalent) checks ensure the mapped
    file object is the intended one.
15. **Stale-generation no-modification test** — an observer holding a stale/mismatched generation
    performs no take, reset, modification, or reclamation of any slot it does **not** own. If it
    had already legitimately acquired a slot (`READY → READING`) before detecting the mismatch, it
    MUST release that owned slot via `READING → FREE` (G.6) without modifying its metadata or
    payload; that release is ownership cleanup, **not** reclamation of a stale/unowned slot.
16. **Multiple concurrent `READY` slots / latest-frame selection** — tests with **2 and 3** slots
    simultaneously `READY` must prove: (a) the slot with the greatest valid `sequence` is the one
    presented; (b) older `READY` (superseded) slots are safely drained via `READY → READING → FREE`
    without being presented; (c) all slots eventually return to `FREE`; (d) the producer can
    continue publishing throughout; (e) no `READY` slot is permanently stranded; and (f) a failed
    `READY → READING` CAS causes a rescan with no direct state modification.
17. **Generation establishment and immutability** — assert the mapping header and all slots are
    initialized before advertisement; that `generation_hi`/`generation_lo` are not changed after
    initialization; that the guest does not access the data plane before establishing and
    validating its expected `Generation`; and that a stale/mismatched `Generation` does not
    authorize slot reclamation.

---

## P. Integration Dependencies

**Mapping identity advertisement (implemented and runtime-validated).**
The approved protocol decision uses the existing `START_STREAM` MessageType (ID 6), with the
payload contract documented in `docs/protocol.md`: non-zero `session_id`, normalized Linux
absolute `mapping_path`, `generation_hi`, and `generation_lo`. No MessageType is added and no
legacy `GRANT`, `READY`, `RELEASE`, or `RESET` TCP messages are introduced.

The host creates and initializes the mapping header and all slots before sending `START_STREAM`.
The guest requires the advertised session to match `HELLO_ACK`, opens the advertised file, checks
its exact size and canonical header, and compares both generation halves before making the mapping
available to data-plane code. The host serializes all outbound messages for a session through one
sequence counter and send lock.

**Current Wine path mapping.**
The current Wine prefix maps `Z:` to the native Linux `/home/pakkanannai/Downloads` directory.
The host's BepInEx configuration exposes both `LinuxSharedDirectory` and `WineSharedDirectory`; the
default pair is `/home/pakkanannai/Downloads` and `Z:\`. These values MUST resolve to the same file
object. The guest opens the advertised Linux path directly. This pair is environment-specific;
native Windows hosting will require its own explicitly defined shared-path contract before that
platform is considered supported end-to-end.

**Other dependencies:**

- Host-side abandoned-mapping cleanup after host process crash (operator-configured policy remains
  required; normal control-session close cleanup is implemented).
- Confirmation of the atomic API set for the exact shipped Wine Mono and Java 21 versions
  (empirically observed; not a formal guarantee).
- Real ULTRAKILL/Wine ↔ Minecraft runtime verification of file identity and reconnect behavior.

## Q. Final Status

- **Canonical M5 shared-buffer specification:** this document; the previous design-review blockers
  were resolved before implementation work began.
- **Current implementation status:** complete for Milestone 5. Production Java/C# shared-buffer
  primitives, `START_STREAM` mapping advertisement, guest identity validation, reconnect behavior,
  focused cross-language tests, and live Minecraft 1.21.1 framebuffer capture are implemented.
  A 2,000-frame concurrent-process stress test passed in both Java→C# and C#→Java directions.
- **Minecraft framebuffer capture is runtime-verified.** The Fabric guest uses a three-PBO OpenGL
  readback ring, zero-timeout fence polling, and a single worker thread for BGRA8 copy/checksum/
  publication. Live runs produced 267 frames at 1366x700 and 33+ frames at 854x480. A reconnect
  run published 9 frames before and 9 frames after a fresh mapping/generation. The render thread
  reserves shared slots using atomic state transitions and never holds the connection lifecycle lock
  during the frame-sized copy. The publisher is drained before mapped PBOs are unmapped at shutdown.
- **The canonical MCUB MessageType enumeration is unchanged; `START_STREAM` uses its existing ID 6.**
- **Existing POCs** (`tools/varhandle_interlocked_poc`, `tools/atomic_ipc_validation`,
  `tools/ring_buffer_validation`, `tools/robust_mutex_interop_validation`) remain **validation
  evidence**, not the production layout.

[executed on device: demo (6087f9ba-7d9b-4f79-a051-e5f6c7cfdde6)]