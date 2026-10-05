using BepInEx;
using BepInEx.Logging;
using BepInEx.Configuration;
using BepInEx.Unity.Mono;
using MinecraftBridge.Protocol;
using MinecraftBridge.Framebuffer;
using System;
using System.Collections.Generic;
using System.IO;
using System.Net;
using UnityEngine;

namespace MinecraftBridge
{
    /// <summary>
    /// Host plugin for ULTRAKILL to bridge Minecraft Java Edition.
    /// Milestone 1: Minimal Host Plugin (Lifecycle, Diagnostics, Safe Shutdown).
    /// </summary>
    [BepInPlugin(PluginGuid, PluginName, PluginVersion)]
    public class MinecraftBridgePlugin : BaseUnityPlugin
    {
        public const string PluginGuid = "com.bridge.minecraft";
        public const string PluginName = "MinecraftBridge";
        public const string PluginVersion = "0.1.0";

        internal static ManualLogSource Log { get; private set; }
        private TcpControlServer _controlServer;
        private readonly object _mappingGate = new object();
        private readonly Dictionary<uint, Tuple<SharedFramebuffer, string, string>> _mappings = new Dictionary<uint, Tuple<SharedFramebuffer, string, string>>();
        private ConfigEntry<string> _linuxSharedDirectory;
        private ConfigEntry<string> _wineSharedDirectory;

        private void Awake()
        {
            Log = Logger;
            Log.LogInfo("==================================================");
            Log.LogInfo($"{PluginName} v{PluginVersion} initializing...");
            Log.LogInfo("==================================================");

            // Log Unity and system diagnostic information
            Log.LogInfo($"Unity Version:          {Application.unityVersion}");
            Log.LogInfo($"Platform:               {Application.platform}");
            Log.LogInfo($"Data Path:              {Application.dataPath}");
            Log.LogInfo($"Graphics Device:        {SystemInfo.graphicsDeviceName} ({SystemInfo.graphicsDeviceType})");
            Log.LogInfo($"Graphics Memory:        {SystemInfo.graphicsMemorySize} MB");
            Log.LogInfo($"System Memory:          {SystemInfo.systemMemorySize} MB");
            Log.LogInfo($"Target Frame Rate:      {Application.targetFrameRate}");
            Log.LogInfo("==================================================");
            Log.LogInfo("Milestone 1 Host Plugin initialized successfully.");
            Log.LogInfo("==================================================");

            try
            {
                _linuxSharedDirectory = Config.Bind("SharedMemory", "LinuxSharedDirectory", "/home/pakkanannai/Downloads",
                    "Native Linux directory advertised to the Minecraft guest.");
                _wineSharedDirectory = Config.Bind("SharedMemory", "WineSharedDirectory", @"Z:\",
                    "Wine drive directory that resolves to the same native directory.");
                _controlServer = new TcpControlServer(IPAddress.Loopback, 47653, message => Log.LogInfo(message));
                _controlServer.SessionEstablished += OnControlSessionEstablished;
                _controlServer.SessionClosed += OnControlSessionClosed;
                _controlServer.Start();
            }
            catch (System.Exception ex)
            {
                Log.LogError("MCUB control server failed to start: " + ex);
                _controlServer?.Dispose();
                _controlServer = null;
            }
        }

        private void OnControlSessionEstablished(TcpControlServer.ControlSession session)
        {
            string fileName = "minecraft-ultrakill-bridge-" + session.SessionId + "-" + Guid.NewGuid().ToString("N") + ".shm";
            string linuxDirectory = _linuxSharedDirectory.Value;
            string wineDirectory = _wineSharedDirectory.Value;
            if (string.IsNullOrEmpty(linuxDirectory) || !linuxDirectory.StartsWith("/") || linuxDirectory.Contains("\\"))
                throw new InvalidOperationException("LinuxSharedDirectory must be a Linux absolute path");
            if (string.IsNullOrEmpty(wineDirectory)) throw new InvalidOperationException("WineSharedDirectory is required");
            string advertisedPath = linuxDirectory.TrimEnd('/') + "/" + fileName;
            // The configured Wine path must resolve to the same file object as advertisedPath.
            string winePath = Path.Combine(wineDirectory, fileName);
            SharedFramebuffer mapping = null;
            try
            {
                mapping = SharedFramebuffer.CreateNew(winePath, session.SessionId);
                if (mapping.SessionId != session.SessionId) throw new InvalidDataException("Created mapping session ID mismatch");
                lock (_mappingGate) _mappings.Add(session.SessionId, Tuple.Create(mapping, advertisedPath, winePath));
                session.Send(new StartStreamMessage
                {
                    SessionId = session.SessionId,
                    MappingPath = advertisedPath,
                    GenerationHi = mapping.GenerationHi,
                    GenerationLo = mapping.GenerationLo
                });
                Log.LogInfo("MCUB_START_STREAM_SENT session=" + session.SessionId + " path=" + advertisedPath);
            }
            catch (Exception ex)
            {
                lock (_mappingGate) _mappings.Remove(session.SessionId);
                if (mapping != null) mapping.Dispose();
                try { File.Delete(winePath); } catch { }
                Log.LogError("MCUB_START_STREAM_FAILED session=" + session.SessionId + " reason=" + ex);
                throw;
            }
        }

        private void OnControlSessionClosed(uint sessionId)
        {
            Tuple<SharedFramebuffer, string, string> entry = null;
            lock (_mappingGate)
            {
                if (_mappings.TryGetValue(sessionId, out entry)) _mappings.Remove(sessionId);
            }
            if (entry == null) return;
            try { entry.Item1.Dispose(); }
            finally
            {
                try { File.Delete(entry.Item3); }
                catch (Exception ex) { Log.LogWarning("Could not delete abandoned mapping " + entry.Item2 + ": " + ex.Message); }
            }
        }

        private void OnEnable()
        {
            Log?.LogInfo($"{PluginName} enabled.");
        }

        private void OnDisable()
        {
            Log?.LogInfo($"{PluginName} disabled.");
        }

        private void OnDestroy()
        {
            Log?.LogInfo($"{PluginName} shutting down safely.");
            _controlServer?.Dispose();
            _controlServer = null;
            uint[] sessions;
            lock (_mappingGate) { sessions = new uint[_mappings.Count]; _mappings.Keys.CopyTo(sessions, 0); }
            foreach (uint session in sessions) OnControlSessionClosed(session);
        }
    }
}
