# Minecraft × ULTRAKILL Bridge Protocol Specification

**Document Version:** 1.0.0  
**Protocol Version:** 1  
**Status:** Canonical wire format implemented; TCP control runtime and START_STREAM mapping identity are integrated (Milestones 3–5).

---

## 1. Design Principles

1. **Binary & Deterministic:** All multi-byte integer and floating-point primitives are encoded in **Little-Endian** byte order (IEEE 754 for floats).
2. **Platform & Language Independent:** Compatible between C# (.NET / Mono) and Java (OpenJDK 21 / JVM) running on Linux and Windows/Wine.
3. **No Process Pointers:** Memory addresses and process-local pointers are never transmitted. A shared mapping is identified once by the `START_STREAM` Linux file path, session ID, and generation; frame slots are then selected locally by slot index and sequence.
4. **Bounded Payloads:** All strings and variable-length collections are explicitly bounded to prevent buffer exhaustion and malformed allocation attacks.
5. **Fail-Safe Version Negotiation:** The protocol header includes a protocol version. Implementations must reject incompatible versions gracefully.

---

## 2. Packet Framing

Every packet transmitted over the control channel consists of a fixed-size **16-byte Header** followed by a variable-length **Payload**:

```text
+-------------------+-------------------+-------------------+-------------------+
| 00..03 Magic      | 04..05 Version    | 06..07 MsgType    | 08..11 SequenceId |
| ("MCUB" = 4B)     | (uint16 = 2B)     | (uint16 = 2B)     | (uint32 = 4B)     |
+-------------------+-------------------+-------------------+-------------------+
| 12..15 PayloadLen | 16..N Payload Data (PayloadLen bytes)                     |
| (uint32 = 4B)     |                                                           |
+-------------------+-----------------------------------------------------------+
```

### 2.1 Header Field Definitions

| Offset | Field | Type | Description |
| :--- | :--- | :--- | :--- |
| `0x00` | `Magic` | `uint32` | Magic identification constant: ASCII `"MCUB"` (`0x4255434D` in Little-Endian). |
| `0x04` | `Version` | `uint16` | Protocol version number (currently `1`). |
| `0x06` | `MessageType` | `uint16` | Enumerated message identifier. |
| `0x08` | `SequenceId` | `uint32` | Monotonically increasing sequence number for message correlation and ordering. |
| `0x0C` | `PayloadLength` | `uint32` | Length of following payload in bytes (maximum permitted: `65536` bytes = 64 KB). |

---

## 3. Message Type Enumeration

| Code | Name | Description |
| :--- | :--- | :--- |
| `1` | `HELLO` | Initial connection handshake from client to host. |
| `2` | `HELLO_ACK` | Handshake acknowledgment from host to client. |
| `3` | `PING` | Liveness heartbeat carrying sender timestamp. |
| `4` | `PONG` | Heartbeat response echoing sender timestamp. |
| `5` | `CAPABILITIES` | Feature negotiation and capability bitmask. |
| `6` | `START_STREAM` | Host requests start of framebuffer streaming. |
| `7` | `STOP_STREAM` | Host requests pause/stop of framebuffer streaming. |
| `8` | `FRAME_METADATA` | Notification of a newly published framebuffer. |
| `9` | `CAMERA_STATE` | Synchronized camera position, rotation, and field of view. |
| `10` | `INPUT_EVENT` | Keyboard, mouse delta, mouse buttons, or scroll event. |
| `11` | `INPUT_FOCUS` | Notification of focus gain or loss with state flush request. |
| `12` | `RAYCAST_REQUEST` | Block targeting ray query. |
| `13` | `RAYCAST_RESPONSE` | Block targeting hit results. |
| `14` | `ENTITY_UPDATE` | Entity discovery and position updates. |
| `15` | `ENTITY_REMOVE` | Entity despawn / out of range. |
| `16` | `DAMAGE_EVENT` | Combat and gameplay damage exchange. |
| `99` | `ERROR` | Protocol or application error notice. |
| `100`| `SHUTDOWN` | Graceful disconnection notice. |

---

## 4. Fundamental Message Payloads

### 4.1 String Serialization Rule
Strings are serialized as:
* `Length` (`uint16`, 2 bytes): length of UTF-8 encoded string in bytes.
* `Bytes` (N bytes): raw UTF-8 bytes.
* Empty string: length = 0. Maximum string length: 1024 bytes.

### 4.2 HELLO (`Type = 1`)
Sent by the connecting party upon establishing control channel:
* `ProtocolVersion` (`uint16`): Sender's expected protocol version (must match host).
* `ClientName` (`string`): Human-readable identifier (e.g., `"MinecraftFabricMod"`).
* `ClientVersion` (`string`): e.g., `"1.21.1-fabric-0.16.5"`.
* `Capabilities` (`uint32`): Capability bitmask flags (e.g. `CAP_FRAME_STREAM = 0x01`, `CAP_INPUT = 0x02`, `CAP_CAMERA = 0x04`).

### 4.3 HELLO_ACK (`Type = 2`)
Sent in response to HELLO:
* `Status` (`uint32`): `0 = OK`, `1 = PROTOCOL_VERSION_MISMATCH`, `2 = REJECTED`.
* `AcceptedVersion` (`uint16`): The negotiated protocol version.
* `SessionId` (`uint32`): Unique identifier for this connection session.
* `ErrorMessage` (`string`): Descriptive error message if `Status != 0`.

### 4.4 PING (`Type = 3`) & PONG (`Type = 4`)
* `TimestampNs` (`uint64`): Nanosecond or millisecond timestamp (Little-Endian `uint64`).

### 4.5 START_STREAM (`Type = 6`)
Sent by the host after creating and fully initializing a new shared framebuffer mapping. This message advertises mapping identity; it does not grant or transfer ownership of any slot.

| Order | Field | Type | Description |
| :--- | :--- | :--- | :--- |
| 1 | `SessionId` | `uint32` | Must be non-zero and must match the `SessionId` received in this connection's `HELLO_ACK`. |
| 2 | `MappingPath` | `string` | UTF-8 Linux absolute path to the shared mapping file. It must be normalized (no `.` or `..` path segments), contain no backslashes, and be non-empty. The host performs Wine path translation locally; the wire value remains the Linux path. |
| 3 | `GenerationHi` | `uint64` | High 64 bits of the mapping's immutable 128-bit generation. |
| 4 | `GenerationLo` | `uint64` | Low 64 bits of the mapping's immutable 128-bit generation. Both generation halves must not be zero. |

Serialization order is exactly the table order, using the canonical little-endian primitives and the standard `uint16`-length-prefixed UTF-8 string encoding. The receiver must reject trailing payload bytes, require the session ID to match its `HELLO_ACK`, open the advertised file, validate the complete mapping header and size, and compare the header's session ID and both generation halves before any data-plane access. A failed check closes the mapping and fails the control session; it never authorizes slot reclamation. The host sends this message only after the mapping header and all slots have been initialized.

### 4.6 FRAME_METADATA (`Type = 8`)
* `BufferId` (`uint32`): Index of the triple-buffer ready for reading (`0`, `1`, or `2`).
* `Width` (`uint32`): Framebuffer width in pixels.
* `Height` (`uint32`): Framebuffer height in pixels.
* `Stride` (`uint32`): Row pitch in bytes (e.g. `Width * 4`).
* `Format` (`uint32`): Pixel format (`1 = RGBA8`, `2 = BGRA8`).
* `SequenceNumber` (`uint64`): Monotonically increasing frame sequence number.
* `TimestampNs` (`uint64`): Frame presentation timestamp.

### 4.6 CAMERA_STATE (`Type = 9`)
* `PosX` (`float64`): Camera X position in world units.
* `PosY` (`float64`): Camera Y position in world units.
* `PosZ` (`float64`): Camera Z position in world units.
* `Yaw` (`float32`): Minecraft camera yaw in degrees.
* `Pitch` (`float32`): Minecraft camera pitch in degrees.
* `Roll` (`float32`): Camera roll in degrees; currently `0` from the guest.
* `Fov` (`float32`): Minecraft camera FOV in degrees.
* `SequenceNumber` (`uint64`): Monotonically increasing guest camera sequence.

Direction is guest-to-host. The guest samples its active camera entity every three Minecraft client ticks and sends the state over the TCP control plane. The host queues the state on the socket worker thread and applies only the newest sequence on Unity's main thread. Host application maps Minecraft `(x, y, z)` to Unity `(x, y, -z)` and converts yaw/pitch/roll into the Unity camera rotation convention. The mirrored Unity camera is disabled and does not replace ULTRAKILL's active gameplay camera; it is the authoritative bridge camera for later interaction/raycast work. The host recreates the bridge camera if a Unity scene transition destroys its temporary scene object.

### 4.7 INPUT_EVENT (`Type = 10`)
* `EventType` (`uint8`): `1 = KEY_DOWN`, `2 = KEY_UP`, `3 = MOUSE_MOVE_RELATIVE`, `4 = MOUSE_DOWN`, `5 = MOUSE_UP`, `6 = MOUSE_WHEEL`.
* `KeyCode` (`uint32`): Standardized key identifier.
* `MouseDx` (`int32`): Relative mouse movement delta X.
* `MouseDy` (`int32`): Relative mouse movement delta Y.
* `MouseButtons` (`uint32`): Bitmask of active mouse buttons (`1 = Left`, `2 = Right`, `4 = Middle`).
* `WheelDelta` (`int32`): Mouse scroll wheel delta.

### 4.8 INPUT_FOCUS (`Type = 11`)
* `HasFocus` (`uint8`): `1 = Focused`, `0 = Unfocused`.
* `ReleaseHeldKeys` (`uint8`): `1 = Force clear all held buttons/keys immediately`.

### 4.9 ERROR (`Type = 99`)
* `ErrorCode` (`uint32`): Numeric error code.
* `ErrorMessage` (`string`): Human-readable error description.

### 4.10 SHUTDOWN (`Type = 100`)
* `ReasonCode` (`uint32`): `0 = NORMAL`, `1 = CRASH`, `2 = RECONNECT`.
* `ReasonText` (`string`): Optional descriptive text.

---

## 5. Milestone 3 TCP session policy

The host control endpoint listens on loopback TCP port `47653`. Each direction uses an independent `SequenceId` counter beginning at `1` for a connection; received sequence values must be contiguous. A sequence counter is not a session identifier and is not shared-memory state.

The host requires HELLO as the first client frame. It validates the header and HELLO payload protocol versions, then returns HELLO_ACK with a nonzero randomly generated `SessionId` for a successful connection. A rejected payload version receives a nonzero status, the supported version, session ID `0`, and an error string. IDs are not reused during one host server lifetime. The client retains the ID as control-session metadata. It is not included in later canonical headers or payloads and does not fence stale access to a shared mapping.

PING is answered with PONG carrying the same timestamp. SHUTDOWN is a one-way notice; the receiver closes that connection. A peer disconnect, malformed frame, unsupported header version, idle receive timeout, or server shutdown closes the connection and releases its socket/session resources. The host endpoint handles HELLO, PING, PONG, ERROR, and SHUTDOWN, and advertises an initialized framebuffer mapping with START_STREAM after a session is established. Other already-defined message types remain canonical but are not yet application-handled by this endpoint.

This endpoint uses canonical `ERROR` payloads for well-formed but unsupported types: error code `1` means unknown message type and code `2` means a known type is not handled by the current control endpoint. These are endpoint-local code values within the existing canonical `ErrorCode` field; they add no message type or payload field.

The TCP connection lifecycle is independent of shared-memory buffer ownership. START_STREAM binds a new mapping path and immutable generation to the negotiated session; it does not transfer slot ownership. On disconnect the host abandons and removes that session's mapping after closing its handles. The guest reconnects with bounded exponential backoff; the host issues a fresh session ID and mapping generation. Neither side reclaims WRITING/READING slots in an abandoned mapping. A new HELLO/HELLO_ACK session ID alone is not a shared-memory fencing or reclamation mechanism.

## 6. Error Handling & Validation Rules

1. **Magic Mismatch:** If the first 4 bytes of a stream or packet do not equal `"MCUB"` (`0x4255434D`), the connection must be dropped immediately.
2. **Payload Bounds:** If `PayloadLength > 65536`, the reader must reject the packet with `ERR_PAYLOAD_TOO_LARGE` without allocating memory.
3. **Truncated Packets:** Deserializers must check buffer boundaries before reading any primitive. Reading past end-of-payload must throw `ProtocolTruncatedException` rather than returning uninitialized data.
4. **Unsupported Message Types:** An unknown message type must be handled gracefully (logged with warning and ignored, or returned with an `ERROR` response) without terminating the connection unless the header itself is unparseable.

### Milestone 7 Input semantics

`INPUT_EVENT` is host-to-guest only. Keyboard `KeyCode` values use GLFW key constants; mouse button events use `KeyCode = 0/1/2` for left/right/middle. `MOUSE_MOVE_RELATIVE` carries signed pixel deltas in `MouseDx`/`MouseDy`; `MOUSE_WHEEL` carries signed vertical wheel units in `WheelDelta`.

`INPUT_FOCUS` is host-to-guest only. `HasFocus=false` requests immediate release of all guest-held keys/buttons when `ReleaseHeldKeys=true`. The guest applies queued input only on its Minecraft client thread.
