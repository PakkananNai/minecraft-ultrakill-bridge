using BepInEx;
using BepInEx.Logging;
using BepInEx.Unity.Mono;
using MinecraftBridge.Protocol;
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
                _controlServer = new TcpControlServer(IPAddress.Loopback, 47653, message => Log.LogInfo(message));
                _controlServer.Start();
            }
            catch (System.Exception ex)
            {
                Log.LogError("MCUB control server failed to start: " + ex);
                _controlServer?.Dispose();
                _controlServer = null;
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
        }
    }
}
