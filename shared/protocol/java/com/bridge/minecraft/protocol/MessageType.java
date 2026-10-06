package com.bridge.minecraft.protocol;

public enum MessageType {
    UNKNOWN(0),
    HELLO(1),
    HELLO_ACK(2),
    PING(3),
    PONG(4),
    CAPABILITIES(5),
    START_STREAM(6),
    STOP_STREAM(7),
    FRAME_METADATA(8),
    CAMERA_STATE(9),
    INPUT_EVENT(10),
    INPUT_FOCUS(11),
    RAYCAST_REQUEST(12),
    RAYCAST_RESPONSE(13),
    ENTITY_UPDATE(14),
    ENTITY_REMOVE(15),
    DAMAGE_EVENT(16),
    ENTITY_INTERACTION(17),
    ERROR(99),
    SHUTDOWN(100);

    private final int id;

    MessageType(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }

    public static MessageType fromId(int id) {
        for (MessageType type : values()) {
            if (type.id == id) {
                return type;
            }
        }
        return UNKNOWN;
    }
}
