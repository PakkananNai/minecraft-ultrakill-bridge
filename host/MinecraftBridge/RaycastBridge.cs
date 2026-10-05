using System;
using MinecraftBridge.Protocol;

namespace MinecraftBridge
{
    /// <summary>Requests Minecraft-authoritative block raycasts and records the latest target.</summary>
    internal sealed class RaycastBridge : IDisposable
    {
        private TcpControlServer.ControlSession _session;
        private ulong _nextRequestId;
        private int _tick;
        private int _responses;
        private RaycastResponseMessage _latest;

        internal void SetSession(TcpControlServer.ControlSession session)
        {
            _session = session;
            _tick = 0;
            _latest = null;
        }

        internal void ClearSession(TcpControlServer.ControlSession session)
        {
            if (ReferenceEquals(_session, session))
            {
                _session = null;
                _latest = null;
            }
        }

        internal void Tick()
        {
            if (_session == null || ++_tick < 3) return;
            _tick = 0;
            try
            {
                ulong requestId = ++_nextRequestId;
                _session.Send(new RaycastRequestMessage { RequestId = requestId, MaxDistance = 6.0f });
                if ((requestId % 30) == 0)
                    MinecraftBridgePlugin.Log?.LogInfo("M9_RAYCAST_REQUEST_SENT count=" + requestId + " maxDistance=6.0");
            }
            catch (Exception ex)
            {
                MinecraftBridgePlugin.Log?.LogWarning("M9_RAYCAST_REQUEST_FAILED " + ex.GetType().Name + ": " + ex.Message);
            }
        }

        internal void OnResponse(TcpControlServer.ControlSession session, RaycastResponseMessage response)
        {
            if (!ReferenceEquals(_session, session)) return;
            _latest = response;
            _responses++;
            if ((_responses % 30) == 0)
            {
                MinecraftBridgePlugin.Log?.LogInfo(
                    "M9_RAYCAST_RESPONSE count=" + _responses
                    + " request=" + response.RequestId
                    + " hit=" + response.Hit
                    + " block=" + response.BlockX + "," + response.BlockY + "," + response.BlockZ
                    + " side=" + response.Side
                    + " distance=" + response.Distance.ToString("F3")
                    + " id=" + response.BlockId);
            }
        }

        internal RaycastResponseMessage Latest => _latest;

        public void Dispose()
        {
            _session = null;
            _latest = null;
        }
    }
}
