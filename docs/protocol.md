# Minecraft × ULTRAKILL Bridge Protocol Specification

**Document Version:** 1.0.0  
**Protocol Version:** 1  
**Status:** Implemented (Milestone 2)

---

## 1. Design Principles

1. **Binary & Deterministic:** All multi-byte integer and floating-point primitives are encoded in **Little-Endian** byte order (IEEE 754 for floats).
2. **Platform & Language Independent:** Compatible between C# (.NET / Mono) and Java (OpenJDK 21 / JVM) running on Linux and Windows/Wine.
3. **No Pointers:** Memory addresses and pointers are never transmitted over the control channel. Shared-memory buffers are referenced strictly via integer buffer IDs, offsets, and sequence counters.
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
| `0x05` | `MessageType` | `uint16` | Enumerated message identifier. |
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
* `ClientVersion` (`string`): e.g., `"1.21.1-fabric-0.19.3"`.
* `Capabilities` (`uint32`): Capability bitmask flags (e.g. `CAP_FRAME_STREAM = 0x01`, `CAP_INPUT = 0x02`, `CAP_CAMERA = 0x04`).

### 4.3 HELLO_ACK (`Type = 2`)
Sent in response to HELLO:
* `Status` (`uint32`): `0 = OK`, `1 = PROTOCOL_VERSION_MISMATCH`, `2 = REJECTED`.
* `AcceptedVersion` (`uint16`): The negotiated protocol version.
* `SessionId` (`uint32`): Unique identifier for this connection session.
* `ErrorMessage` (`string`): Descriptive error message if `Status != 0`.

### 4.4 PING (`Type = 3`) & PONG (`Type = 4`)
* `TimestampNs` (`uint64`): Nanosecond or millisecond timestamp (Little-Endian `uint64`).

### 4.5 FRAME_METADATA (`Type = 8`)
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
* `Yaw` (`float32`): Rotation yaw in degrees.
* `Pitch` (`float32`): Rotation pitch in degrees.
* `Roll` (`float32`): Rotation roll in degrees.
* `Fov` (`float32`): Vertical or horizontal field of view in degrees.
* `SequenceNumber` (`uint64`): Frame/tick sequence number.

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

## 5. Error Handling & Validation Rules

1. **Magic Mismatch:** If the first 4 bytes of a stream or packet do not equal `"MCUB"` (`0x4255434D`), the connection must be dropped immediately.
2. **Payload Bounds:** If `PayloadLength > 65536`, the reader must reject the packet with `ERR_PAYLOAD_TOO_LARGE` without allocating memory.
3. **Truncated Packets:** Deserializers must check buffer boundaries before reading any primitive. Reading past end-of-payload must throw `ProtocolTruncatedException` rather than returning uninitialized data.
4. **Unsupported Message Types:** An unknown message type must be handled gracefully (logged with warning and ignored, or returned with an `ERROR` response) without terminating the connection unless the header itself is unparseable.
