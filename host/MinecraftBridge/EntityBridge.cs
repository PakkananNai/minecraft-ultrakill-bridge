using System;
using System.Collections.Generic;
using MinecraftBridge.Protocol;

namespace MinecraftBridge
{
    internal sealed class EntityBridge : IDisposable
    {
        internal sealed class EntityState
        {
            internal uint Id;
            internal string Type;
            internal double X, Y, Z;
            internal float Yaw, Pitch;
            internal float VelocityX, VelocityY, VelocityZ;
            internal float Health, MaxHealth;
            internal byte Flags;
        }

        private readonly object _gate = new object();
        private readonly Dictionary<uint, EntityState> _entities = new Dictionary<uint, EntityState>();
        private uint _updates;
        private uint _removes;
        private uint _interactions;

        internal void SetSession(TcpControlServer.ControlSession session)
        {
            lock (_gate) _entities.Clear();
            _updates = _removes = _interactions = 0;
        }

        internal void ClearSession(TcpControlServer.ControlSession session)
        {
            lock (_gate) _entities.Clear();
        }

        internal void OnUpdate(TcpControlServer.ControlSession session, EntityUpdateMessage message)
        {
            bool spawned;
            lock (_gate)
            {
                spawned = !_entities.ContainsKey(message.EntityId);
                _entities[message.EntityId] = new EntityState
                {
                    Id = message.EntityId, Type = message.EntityType,
                    X = message.PosX, Y = message.PosY, Z = message.PosZ,
                    Yaw = message.Yaw, Pitch = message.Pitch,
                    VelocityX = message.VelocityX, VelocityY = message.VelocityY, VelocityZ = message.VelocityZ,
                    Flags = message.Flags
                };
                _updates++;
            }

            if (spawned || (_updates % 30) == 0)
                MinecraftBridgePlugin.Log?.LogInfo(
                    "M10_ENTITY_UPDATE session=" + session.SessionId
                    + " id=" + message.EntityId + " type=" + message.EntityType
                    + " pos=" + message.PosX.ToString("F2") + "," + message.PosY.ToString("F2") + "," + message.PosZ.ToString("F2")
                    + (spawned ? " spawned=true" : " spawned=false"));
        }

        internal void OnDamage(TcpControlServer.ControlSession session, DamageEventMessage message)
        {
            lock (_gate)
            {
                EntityState state;
                if (_entities.TryGetValue(message.TargetId, out state))
                {
                    state.Health = message.Health;
                    state.MaxHealth = message.MaxHealth;
                }
            }

            MinecraftBridgePlugin.Log?.LogInfo(
                "M11_DAMAGE_EVENT session=" + session.SessionId
                + " type=" + message.EventType
                + " target=" + message.TargetId
                + " attacker=" + message.AttackerId
                + " amount=" + message.Amount.ToString("F2")
                + " health=" + message.Health.ToString("F2")
                + "/" + message.MaxHealth.ToString("F2")
                + " source=" + message.SourceType);
        }

        internal void OnRemove(TcpControlServer.ControlSession session, EntityRemoveMessage message)
        {
            bool removed;
            lock (_gate)
            {
                removed = _entities.Remove(message.EntityId);
                _removes++;
            }
            if (removed)
                MinecraftBridgePlugin.Log?.LogInfo("M10_ENTITY_REMOVE session=" + session.SessionId + " id=" + message.EntityId);
        }

        internal void OnInteraction(TcpControlServer.ControlSession session, EntityInteractionMessage message)
        {
            lock (_gate) _interactions++;
            MinecraftBridgePlugin.Log?.LogInfo(
                "M10_ENTITY_INTERACTION session=" + session.SessionId
                + " id=" + message.EntityId + " type=" + message.InteractionType + " hand=" + message.Hand);
        }

        internal int Count
        {
            get { lock (_gate) return _entities.Count; }
        }

        public void Dispose()
        {
            lock (_gate) _entities.Clear();
        }
    }
}
