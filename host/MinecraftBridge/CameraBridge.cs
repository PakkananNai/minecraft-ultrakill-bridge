using System;
using MinecraftBridge.Protocol;
using UnityEngine;

namespace MinecraftBridge
{
    /// <summary>
    /// Queues Minecraft-authoritative camera state from the TCP worker and applies
    /// the latest state on Unity's main thread.
    /// </summary>
    internal sealed class CameraBridge : IDisposable
    {
        private readonly object _gate = new object();
        private GameObject _root;
        private Camera _camera;
        private CameraStateMessage _pending;
        private ulong _lastSequence;
        private int _appliedCount;

        internal CameraBridge()
        {
            CreateCameraObject();
            MinecraftBridgePlugin.Log?.LogInfo(
                "M8_CAMERA_BRIDGE_CREATED authoritative=guest coordinateMap=(x,y,z)->(x,y,-z)");
        }

        private void CreateCameraObject()
        {
            _root = new GameObject("MinecraftBridgeCamera");
            UnityEngine.Object.DontDestroyOnLoad(_root);
            _camera = _root.AddComponent<Camera>();
            _camera.enabled = false;
            _camera.nearClipPlane = 0.05f;
            _camera.farClipPlane = 1000f;
        }

        internal void Enqueue(CameraStateMessage state)
        {
            lock (_gate)
            {
                if (state.SequenceNumber > _lastSequence &&
                    (_pending == null || state.SequenceNumber > _pending.SequenceNumber))
                {
                    _pending = state;
                }
            }
        }

        internal void Update()
        {
            CameraStateMessage state;
            lock (_gate)
            {
                state = _pending;
                _pending = null;
            }

            if (state == null || state.SequenceNumber <= _lastSequence) return;
            _lastSequence = state.SequenceNumber;
            try
            {
                if (_root == null || _camera == null || _root.transform == null)
                    CreateCameraObject();
                _root.transform.position = new Vector3(
                    (float)state.PosX,
                    (float)state.PosY,
                    (float)-state.PosZ);

                _root.transform.rotation = Quaternion.Euler(
                    state.Pitch,
                    180f - state.Yaw,
                    -state.Roll);

                if (state.Fov > 1f && state.Fov < 179f)
                    _camera.fieldOfView = state.Fov;

                _appliedCount++;
                if ((_appliedCount % 30) == 0)
                    MinecraftBridgePlugin.Log?.LogInfo(
                        "M8_CAMERA_APPLIED count=" + _appliedCount + " seq=" + state.SequenceNumber
                        + " unityPos=" + _root.transform.position
                        + " unityEuler=" + _root.transform.eulerAngles
                        + " fov=" + _camera.fieldOfView.ToString("F2"));
            }
            catch (Exception ex)
            {
                MinecraftBridgePlugin.Log?.LogError("M8_CAMERA_APPLY_FAILED seq=" + state.SequenceNumber + " reason=" + ex);
            }
        }

        public void Dispose()
        {
            if (_root != null) UnityEngine.Object.Destroy(_root);
        }
    }
}
