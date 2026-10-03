namespace MinecraftBridge.Protocol
{
    public enum MessageType : ushort
    {
        Unknown = 0,
        Hello = 1,
        HelloAck = 2,
        Ping = 3,
        Pong = 4,
        Capabilities = 5,
        StartStream = 6,
        StopStream = 7,
        FrameMetadata = 8,
        CameraState = 9,
        InputEvent = 10,
        InputFocus = 11,
        RaycastRequest = 12,
        RaycastResponse = 13,
        EntityUpdate = 14,
        EntityRemove = 15,
        DamageEvent = 16,
        Error = 99,
        Shutdown = 100
    }
}
