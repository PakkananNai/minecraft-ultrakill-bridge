using System;
using System.Collections.Generic;
using MinecraftBridge.Protocol;
using UnityEngine;
using UnityEngine.InputSystem;
using UnityEngine.InputSystem.Controls;

namespace MinecraftBridge
{
    /// <summary>Milestone 7 host-side keyboard/mouse capture and focus switching.</summary>
    internal sealed class InputBridge
    {
        private readonly Action<string> _log;
        private readonly List<UnityEngine.InputSystem.InputAction> _disabledActions = new List<UnityEngine.InputSystem.InputAction>();
        private TcpControlServer.ControlSession _session;
        private bool _guestFocus;
        private bool _loggedInputBackend;
        private readonly bool[] _loggedEventTypes = new bool[7];

        internal InputBridge(Action<string> log) { _log = log; }


        internal void SetSession(TcpControlServer.ControlSession session)
        {
            _session = session;
            SendFocus(false, true);
        }

        internal void ClearSession(TcpControlServer.ControlSession session)
        {
            if (ReferenceEquals(_session, session))
            {
                SetGuestFocus(false, true);
                _session = null;
            }
        }

        internal void Tick()
        {
            Keyboard keyboard = Keyboard.current;
            if (keyboard == null) return;
            if (!_loggedInputBackend)
            {
                _loggedInputBackend = true;
                _log?.Invoke("M7_INPUT_BACKEND active=Unity.InputSystem");
            }

            if (keyboard.f8Key.wasPressedThisFrame)
                SetGuestFocus(!_guestFocus, true);

            if (!_guestFocus || _session == null) return;

            foreach (KeyControl key in keyboard.allKeys)
            {
                int glfw = ToGlfwKey(key.keyCode.ToString());
                if (glfw < 0) continue;
                if (key.keyCode != Key.F8)
                {
                    if (key.wasPressedThisFrame) Send(new InputEventMessage { EventType = 1, KeyCode = (uint)glfw });
                    if (key.wasReleasedThisFrame) Send(new InputEventMessage { EventType = 2, KeyCode = (uint)glfw });
                }
            }

            Mouse mouse = Mouse.current;
            if (mouse == null) return;
            Vector2 delta = mouse.delta.ReadValue();
            if (delta.sqrMagnitude > 0.0001f)
                Send(new InputEventMessage { EventType = 3, MouseDx = Mathf.RoundToInt(delta.x), MouseDy = Mathf.RoundToInt(delta.y) });
            if (mouse.leftButton.wasPressedThisFrame) SendMouseButton(4, 0);
            if (mouse.leftButton.wasReleasedThisFrame) SendMouseButton(5, 0);
            if (mouse.rightButton.wasPressedThisFrame) SendMouseButton(4, 1);
            if (mouse.rightButton.wasReleasedThisFrame) SendMouseButton(5, 1);
            if (mouse.middleButton.wasPressedThisFrame) SendMouseButton(4, 2);
            if (mouse.middleButton.wasReleasedThisFrame) SendMouseButton(5, 2);
            Vector2 scroll = mouse.scroll.ReadValue();
            if (Mathf.Abs(scroll.y) > 0.0001f)
            {
                int wheel = Mathf.RoundToInt(scroll.y);
                if (Mathf.Abs(wheel) >= 120) wheel = wheel < 0 ? -1 : 1;
                Send(new InputEventMessage { EventType = 6, WheelDelta = wheel });
            }
        }

        internal void Dispose()
        {
            SetGuestFocus(false, true);
            _session = null;
        }

        private void SendMouseButton(byte eventType, int button)
        {
            Send(new InputEventMessage { EventType = eventType, KeyCode = (uint)button });
        }

        private void SendFocus(bool focus, bool releaseHeldKeys)
        {
            Send(new InputFocusMessage { HasFocus = focus, ReleaseHeldKeys = releaseHeldKeys });
        }

        private void SetGuestFocus(bool focus, bool releaseHeldKeys)
        {
            if (_guestFocus == focus && !releaseHeldKeys) return;
            _guestFocus = focus;
            if (focus)
            {
                _disabledActions.Clear();
                InputSystem.ListEnabledActions(_disabledActions);
                InputSystem.DisableAllEnabledActions();
            }
            else
            {
                for (int i = 0; i < _disabledActions.Count; i++)
                    if (_disabledActions[i] != null) _disabledActions[i].Enable();
                _disabledActions.Clear();
            }
            SendFocus(focus, releaseHeldKeys);
            _log?.Invoke("M7_INPUT_FOCUS focus=" + focus + " releaseHeldKeys=" + releaseHeldKeys);
        }

        private void Send(IMessage message)
        {
            TcpControlServer.ControlSession session = _session;
            if (session == null) return;
            try
            {
                session.Send(message);
                var input = message as InputEventMessage;
                if (input != null && input.EventType < _loggedEventTypes.Length && !_loggedEventTypes[input.EventType])
                {
                    _loggedEventTypes[input.EventType] = true;
                    _log?.Invoke("M7_INPUT_EVENT_SENT type=" + input.EventType + " key=" + input.KeyCode + " dx=" + input.MouseDx + " dy=" + input.MouseDy + " wheel=" + input.WheelDelta);
                }
            }
            catch (Exception ex) { _log?.Invoke("M7_INPUT_SEND_FAILED " + ex.GetType().Name + ": " + ex.Message); }
        }

        private static int ToGlfwKey(string key)
        {
            if (key.Length == 1 && key[0] >= 'A' && key[0] <= 'Z') return key[0];
            if (key.Length == 1 && key[0] >= '0' && key[0] <= '9') return key[0];
            if (key.StartsWith("Digit") && key.Length == 6 && key[5] >= '0' && key[5] <= '9') return key[5];
            if (key.StartsWith("F") && int.TryParse(key.Substring(1), out int f) && f >= 1 && f <= 25) return 290 + f - 1;
            switch (key)
            {
                case "Space": return 32; case "Apostrophe": return 39; case "Comma": return 44; case "Minus": return 45;
                case "Period": return 46; case "Slash": return 47; case "Semicolon": return 59; case "Equals": return 61;
                case "LeftBracket": return 91; case "Backslash": return 92; case "RightBracket": return 93; case "Backquote": return 96;
                case "Escape": return 256; case "Enter": return 257; case "Tab": return 258; case "Backspace": return 259;
                case "Insert": return 260; case "Delete": return 261; case "RightArrow": return 262; case "LeftArrow": return 263;
                case "DownArrow": return 264; case "UpArrow": return 265; case "PageUp": return 266; case "PageDown": return 267;
                case "Home": return 268; case "End": return 269; case "CapsLock": return 280; case "ScrollLock": return 281;
                case "NumLock": return 282; case "PrintScreen": return 283; case "Pause": return 284;
                case "LeftShift": return 340; case "LeftCtrl": return 341; case "LeftAlt": return 342; case "LeftMeta": return 343;
                case "RightShift": return 344; case "RightCtrl": return 345; case "RightAlt": return 346; case "RightMeta": return 347;
                case "ContextMenu": return 348;
                case "Numpad0": return 320; case "Numpad1": return 321; case "Numpad2": return 322; case "Numpad3": return 323;
                case "Numpad4": return 324; case "Numpad5": return 325; case "Numpad6": return 326; case "Numpad7": return 327;
                case "Numpad8": return 328; case "Numpad9": return 329; case "NumpadDecimal": return 330;
                case "NumpadDivide": return 331; case "NumpadMultiply": return 332; case "NumpadSubtract": return 333;
                case "NumpadAdd": return 334; case "NumpadEnter": return 335; case "NumpadEquals": return 336;
                default: return -1;
            }
        }
    }
}
