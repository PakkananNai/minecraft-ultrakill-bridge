using BepInEx;
using BepInEx.Logging;
using BepInEx.Configuration;
using BepInEx.Unity.Mono;
using MinecraftBridge.Protocol;
using MinecraftBridge.Framebuffer;
using System;
using System.Collections;
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
        private readonly object _renderGate = new object();
        private Texture2D _minecraftTexture;
        private Canvas _minecraftCanvas;
        private UnityEngine.UI.RawImage _minecraftRawImage;
        private ulong _lastRenderedSequence;
        private int _lastRenderedWidth;
        private int _lastRenderedHeight;
        private int _renderedFrames;
        private float _nextRenderLogTime;
        private bool _capturedM6Screenshot;
        private InputBridge _inputBridge;
        private CameraBridge _cameraBridge;
        private readonly Dictionary<uint, Tuple<SharedFramebuffer, string, string>> _mappings = new Dictionary<uint, Tuple<SharedFramebuffer, string, string>>();
        private readonly Dictionary<uint, TcpControlServer.ControlSession> _sessions = new Dictionary<uint, TcpControlServer.ControlSession>();
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
                _controlServer.CameraStateReceived += OnCameraStateReceived;
                _controlServer.SessionClosed += OnControlSessionClosed;
                _controlServer.Start();
                CreateMinecraftSurface();
                _inputBridge = new InputBridge(message => Log.LogInfo(message));
                _cameraBridge = new CameraBridge();
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
                lock (_mappingGate)
                {
                    _mappings.Add(session.SessionId, Tuple.Create(mapping, advertisedPath, winePath));
                    _sessions[session.SessionId] = session;
                }
                _inputBridge?.SetSession(session);
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

        private void OnCameraStateReceived(TcpControlServer.ControlSession session, CameraStateMessage camera)
        {
            _cameraBridge?.Enqueue(camera);
            Log.LogInfo("M8_CAMERA_STATE_RECEIVED session=" + session.SessionId
                + " seq=" + camera.SequenceNumber);
        }

        private void OnControlSessionClosed(uint sessionId)
        {
            Tuple<SharedFramebuffer, string, string> entry = null;
            lock (_mappingGate)
            {
                if (_mappings.TryGetValue(sessionId, out entry)) _mappings.Remove(sessionId);
                TcpControlServer.ControlSession session;
                if (_sessions.TryGetValue(sessionId, out session)) _sessions.Remove(sessionId);
                _inputBridge?.ClearSession(session);
            }
            if (entry == null) return;
            try { entry.Item1.Dispose(); }
            finally
            {
                try { File.Delete(entry.Item3); }
                catch (Exception ex) { Log.LogWarning("Could not delete abandoned mapping " + entry.Item2 + ": " + ex.Message); }
            }
        }

        private void CreateMinecraftSurface()
        {
            if (_minecraftCanvas != null) return;

            GameObject canvasObject = new GameObject("MinecraftBridgeCanvas");
            canvasObject.transform.SetParent(transform, false);
            _minecraftCanvas = canvasObject.AddComponent<Canvas>();
            _minecraftCanvas.renderMode = RenderMode.ScreenSpaceOverlay;
            _minecraftCanvas.sortingOrder = 30000;

            GameObject imageObject = new GameObject("MinecraftBridgeRawImage");
            imageObject.transform.SetParent(canvasObject.transform, false);
            _minecraftRawImage = imageObject.AddComponent<UnityEngine.UI.RawImage>();
            RectTransform rect = _minecraftRawImage.rectTransform;
            rect.anchorMin = Vector2.zero;
            rect.anchorMax = Vector2.one;
            rect.offsetMin = Vector2.zero;
            rect.offsetMax = Vector2.zero;
            _minecraftRawImage.raycastTarget = false;

            Log.LogInfo("M6_RENDER_SURFACE_CREATED type=ScreenSpaceOverlay");
        }

        private void Update()
        {
            _inputBridge?.Tick();
            _cameraBridge?.Update();
            if (_mappings.Count == 0) return;

            Tuple<SharedFramebuffer, string, string> entry = null;
            lock (_mappingGate)
            {
                foreach (Tuple<SharedFramebuffer, string, string> candidate in _mappings.Values)
                {
                    entry = candidate;
                    break;
                }
            }

            if (entry == null) return;

            try
            {
                SharedFramebuffer.FrameMetadata metadata;
                byte[] payload;
                if (!entry.Item1.TryReadLatestFrame(out metadata, out payload)) return;

                lock (_renderGate)
                {
                    EnsureMinecraftTexture(metadata.Width, metadata.Height);
                    _minecraftTexture.LoadRawTextureData(payload);
                    _minecraftTexture.Apply(false, false);
                    _minecraftRawImage.texture = _minecraftTexture;
                    _lastRenderedSequence = metadata.Sequence;
                    _lastRenderedWidth = metadata.Width;
                    _lastRenderedHeight = metadata.Height;
                    _renderedFrames++;
                    if (!_capturedM6Screenshot && _renderedFrames >= 100)
                    {
                        string screenshotPath = Path.Combine(Paths.GameRootPath, "m6_frame_presented.png");
                        StartCoroutine(CaptureM6ScreenshotAfterFrame(screenshotPath));
                        _capturedM6Screenshot = true;
                        Log.LogInfo("M6_SCREENSHOT_REQUESTED path=" + screenshotPath);
                    }
                }

                if (Time.unscaledTime >= _nextRenderLogTime)
                {
                    Log.LogInfo("M6_FRAME_PRESENTED sequence=" + _lastRenderedSequence
                        + " size=" + _lastRenderedWidth + "x" + _lastRenderedHeight
                        + " bytes=" + payload.Length + " rendered=" + _renderedFrames);
                    _nextRenderLogTime = Time.unscaledTime + 1f;
                }
            }
            catch (Exception ex)
            {
                Log.LogError("M6_FRAME_PRESENT_FAILED: " + ex);
            }
        }

        private IEnumerator CaptureM6ScreenshotAfterFrame(string path)
        {
            yield return new WaitForEndOfFrame();
            ScreenCapture.CaptureScreenshot(path);
            Log.LogInfo("M6_SCREENSHOT_CAPTURED path=" + path);
        }

        private void EnsureMinecraftTexture(int width, int height)
        {
            if (_minecraftTexture != null && _minecraftTexture.width == width && _minecraftTexture.height == height)
                return;

            if (_minecraftTexture != null)
            {
                Destroy(_minecraftTexture);
                _minecraftTexture = null;
            }

            _minecraftTexture = new Texture2D(width, height, TextureFormat.BGRA32, false, false);
            _minecraftTexture.wrapMode = TextureWrapMode.Clamp;
            _minecraftTexture.filterMode = FilterMode.Point;
            _minecraftTexture.name = "MinecraftBridgeTexture";
            Log.LogInfo("M6_TEXTURE_CREATED size=" + width + "x" + height);
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
            _inputBridge?.Dispose();
            _inputBridge = null;
            _cameraBridge?.Dispose();
            _cameraBridge = null;
            _controlServer?.Dispose();
            _controlServer = null;
            if (_minecraftTexture != null) { Destroy(_minecraftTexture); _minecraftTexture = null; }
            if (_minecraftCanvas != null) { Destroy(_minecraftCanvas.gameObject); _minecraftCanvas = null; }
            _minecraftRawImage = null;
            uint[] sessions;
            lock (_mappingGate) { sessions = new uint[_mappings.Count]; _mappings.Keys.CopyTo(sessions, 0); }
            foreach (uint session in sessions) OnControlSessionClosed(session);
        }
    }
}
