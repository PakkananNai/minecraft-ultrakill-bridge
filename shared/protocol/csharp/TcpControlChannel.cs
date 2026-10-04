using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Net;
using System.Net.Sockets;
using System.Security.Cryptography;
using System.Threading;

namespace MinecraftBridge.Protocol
{
    /// <summary>
    /// Canonical MCUB control-plane listener. Buffer ownership and shared-memory
    /// state are deliberately outside this class.
    /// </summary>
    public sealed class TcpControlServer : IDisposable
    {
        private readonly IPAddress _address;
        private readonly int _port;
        private readonly int _ioTimeoutMilliseconds;
        private readonly Action<string> _log;
        private readonly object _gate = new object();
        private readonly HashSet<uint> _issuedSessions = new HashSet<uint>();
        private readonly List<TcpClient> _clients = new List<TcpClient>();
        private readonly List<Thread> _workers = new List<Thread>();
        private TcpListener _listener;
        private Thread _acceptThread;
        private volatile bool _stopping;

        public TcpControlServer(IPAddress address, int port, Action<string> log = null, int ioTimeoutMilliseconds = 15000)
        {
            _address = address ?? throw new ArgumentNullException(nameof(address));
            if (port < 0 || port > 65535) throw new ArgumentOutOfRangeException(nameof(port));
            if (ioTimeoutMilliseconds <= 0) throw new ArgumentOutOfRangeException(nameof(ioTimeoutMilliseconds));
            _port = port;
            _log = log;
            _ioTimeoutMilliseconds = ioTimeoutMilliseconds;
        }

        public int BoundPort
        {
            get
            {
                if (_listener == null) return _port;
                return ((IPEndPoint)_listener.LocalEndpoint).Port;
            }
        }

        public void Start()
        {
            if (_listener != null) throw new InvalidOperationException("Control server already started");
            _listener = new TcpListener(_address, _port);
            _listener.Start();
            _acceptThread = new Thread(AcceptLoop) { IsBackground = true, Name = "MCUB TCP accept" };
            _acceptThread.Start();
            Log("MCUB_LISTENING address=" + _address + " port=" + BoundPort);
        }

        private void AcceptLoop()
        {
            while (!_stopping)
            {
                TcpClient client = null;
                try
                {
                    client = _listener.AcceptTcpClient();
                    client.NoDelay = true;
                    client.ReceiveTimeout = _ioTimeoutMilliseconds;
                    client.SendTimeout = _ioTimeoutMilliseconds;
                    TcpClient accepted = client;
                    Thread worker = new Thread(() => HandleClient(accepted))
                    {
                        IsBackground = true,
                        Name = "MCUB TCP session"
                    };
                    lock (_gate)
                    {
                        if (_stopping) { client.Close(); break; }
                        _clients.Add(client);
                        _workers.Add(worker);
                        worker.Start();
                    }
                }
                catch (SocketException) when (_stopping) { break; }
                catch (ObjectDisposedException) when (_stopping) { break; }
                catch (Exception ex)
                {
                    if (client != null) CloseClient(client);
                    if (!_stopping) Log("MCUB_ACCEPT_ERROR " + ex.GetType().Name + ": " + ex.Message);
                }
            }
        }

        private void HandleClient(TcpClient client)
        {
            uint tx = 1;
            uint rx = 1;
            uint sessionId = 0;
            try
            {
                NetworkStream stream = client.GetStream();
                ProtocolFrame first = ProtocolStream.ReadFrame(stream);
                RequireNext(ref rx, first.Header.SequenceId);
                if (first.Header.Type != MessageType.Hello)
                    throw new ProtocolValidationException("First message must be HELLO");

                HelloMessage hello;
                using (PacketReader reader = new PacketReader(first.Payload))
                {
                    hello = HelloMessage.Deserialize(reader);
                    RequirePayloadEnd(reader);
                }

                if (hello.ProtocolVersion != ProtocolConstants.CurrentVersion)
                {
                    SendHelloAck(stream, ref tx, 1, 0, "Protocol version mismatch");
                    Log("MCUB_HANDSHAKE_REJECT client=" + hello.ClientName + " version=" + hello.ProtocolVersion);
                    return;
                }

                sessionId = NewSessionId();
                SendHelloAck(stream, ref tx, 0, sessionId, string.Empty);
                Log("MCUB_SESSION_ESTABLISHED id=" + sessionId + " client=" + hello.ClientName + " version=" + hello.ClientVersion);

                while (!_stopping)
                {
                    ProtocolFrame frame = ProtocolStream.ReadFrame(stream);
                    RequireNext(ref rx, frame.Header.SequenceId);
                    if (!IsCanonicalType(frame.Header.Type))
                    {
                        SendError(stream, ref tx, 1, "Unsupported message type " + (ushort)frame.Header.Type);
                        Log("MCUB_UNKNOWN_TYPE type=" + (ushort)frame.Header.Type);
                        continue;
                    }

                    switch (frame.Header.Type)
                    {
                        case MessageType.Ping:
                            PingMessage ping = ReadPing(frame.Payload);
                            WriteMessage(stream, new PongMessage { TimestampNs = ping.TimestampNs }, ref tx);
                            break;
                        case MessageType.Pong:
                            ReadPing(frame.Payload); // Validate canonical timestamp payload.
                            break;
                        case MessageType.Shutdown:
                            ReadShutdown(frame.Payload);
                            Log("MCUB_PEER_SHUTDOWN session=" + sessionId);
                            return;
                        case MessageType.Error:
                            ReadError(frame.Payload);
                            Log("MCUB_PEER_ERROR session=" + sessionId);
                            break;
                        default:
                            SendError(stream, ref tx, 2, "Message is not handled by the Milestone 3 control endpoint");
                            break;
                    }
                }
            }
            catch (Exception ex)
            {
                if (!_stopping) Log("MCUB_SESSION_CLOSED session=" + sessionId + " reason=" + ex.GetType().Name + ": " + ex.Message);
            }
            finally
            {
                CloseClient(client);
                if (sessionId != 0) Log("MCUB_SESSION_CLEANUP id=" + sessionId);
                lock (_gate) _workers.Remove(Thread.CurrentThread);
            }
        }

        private uint NewSessionId()
        {
            lock (_gate)
            {
                using (RandomNumberGenerator rng = RandomNumberGenerator.Create())
                {
                    byte[] bytes = new byte[4];
                    uint value;
                    do
                    {
                        rng.GetBytes(bytes);
                        value = (uint)(bytes[0] | bytes[1] << 8 | bytes[2] << 16 | bytes[3] << 24);
                    } while (value == 0 || !_issuedSessions.Add(value));
                    return value;
                }
            }
        }

        private static void SendHelloAck(Stream stream, ref uint sequence, uint status, uint sessionId, string error)
        {
            HelloAckMessage ack = new HelloAckMessage
            {
                Status = status,
                AcceptedVersion = ProtocolConstants.CurrentVersion,
                SessionId = sessionId,
                ErrorMessage = error
            };
            WriteMessage(stream, ack, ref sequence);
        }

        private static void SendError(Stream stream, ref uint sequence, uint code, string description)
        {
            WriteMessage(stream, new ErrorMessage { ErrorCode = code, Description = description }, ref sequence);
        }

        internal static void WriteMessage(Stream stream, IMessage message, ref uint sequence)
        {
            using (PacketWriter writer = new PacketWriter())
            {
                message.Serialize(writer);
                ProtocolStream.WriteFrame(stream, message.MessageType, sequence, writer.ToPayloadArray());
            }
            IncrementSequence(ref sequence);
        }

        internal static void RequireNext(ref uint expected, uint received)
        {
            if (received != expected)
                throw new ProtocolValidationException("Expected sequence " + expected + ", received " + received);
            IncrementSequence(ref expected);
        }

        internal static void IncrementSequence(ref uint sequence)
        {
            if (sequence == uint.MaxValue) throw new ProtocolValidationException("SequenceId exhausted for this connection");
            sequence++;
        }

        internal static bool IsCanonicalType(MessageType type)
        {
            switch (type)
            {
                case MessageType.Hello: case MessageType.HelloAck: case MessageType.Ping: case MessageType.Pong:
                case MessageType.Capabilities: case MessageType.StartStream: case MessageType.StopStream:
                case MessageType.FrameMetadata: case MessageType.CameraState: case MessageType.InputEvent:
                case MessageType.InputFocus: case MessageType.RaycastRequest: case MessageType.RaycastResponse:
                case MessageType.EntityUpdate: case MessageType.EntityRemove: case MessageType.DamageEvent:
                case MessageType.Error: case MessageType.Shutdown: return true;
                default: return false;
            }
        }

        internal static void RequirePayloadEnd(PacketReader reader)
        {
            if (reader.Remaining != 0) throw new ProtocolValidationException("Unexpected trailing payload bytes");
        }

        internal static PingMessage ReadPing(byte[] payload)
        {
            using (PacketReader reader = new PacketReader(payload))
            {
                PingMessage ping = PingMessage.Deserialize(reader);
                RequirePayloadEnd(reader);
                return ping;
            }
        }

        internal static void ReadShutdown(byte[] payload)
        {
            using (PacketReader reader = new PacketReader(payload))
            {
                ShutdownMessage.Deserialize(reader);
                RequirePayloadEnd(reader);
            }
        }

        internal static void ReadError(byte[] payload)
        {
            using (PacketReader reader = new PacketReader(payload))
            {
                ErrorMessage.Deserialize(reader);
                RequirePayloadEnd(reader);
            }
        }

        private void CloseClient(TcpClient client)
        {
            lock (_gate) _clients.Remove(client);
            try { client.Close(); } catch { }
        }

        private void Log(string message) { if (_log != null) _log(message); }

        public void Stop()
        {
            if (_stopping) return;
            _stopping = true;
            if (_listener != null) _listener.Stop();
            Thread[] workers;
            lock (_gate)
            {
                foreach (TcpClient client in _clients.ToArray())
                {
                    try { client.Close(); } catch { }
                }
                _clients.Clear();
                workers = _workers.ToArray();
            }
            if (_acceptThread != null && _acceptThread != Thread.CurrentThread)
                _acceptThread.Join(_ioTimeoutMilliseconds);
            foreach (Thread worker in workers)
                if (worker != Thread.CurrentThread) worker.Join(_ioTimeoutMilliseconds);
        }

        public void Dispose() { Stop(); }
    }

    /// <summary>Canonical MCUB client used by production guest integrations.</summary>
    public sealed class TcpControlClient : IDisposable
    {
        private readonly TcpClient _client;
        private readonly NetworkStream _stream;
        private uint _tx = 1;
        private uint _rx = 1;
        private bool _closed;

        private TcpControlClient(TcpClient client)
        {
            _client = client;
            _stream = client.GetStream();
        }

        public uint SessionId { get; private set; }

        public static TcpControlClient Connect(string host, int port, string clientName, string clientVersion, uint capabilities, int timeoutMilliseconds = 5000)
        {
            TcpClient tcp = new TcpClient();
            tcp.NoDelay = true;
            tcp.ReceiveTimeout = timeoutMilliseconds;
            tcp.SendTimeout = timeoutMilliseconds;
            try
            {
                IAsyncResult pending = tcp.BeginConnect(host, port, null, null);
                using (pending.AsyncWaitHandle)
                {
                    if (!pending.AsyncWaitHandle.WaitOne(timeoutMilliseconds))
                        throw new TimeoutException("Timed out connecting to MCUB control server");
                    tcp.EndConnect(pending);
                }
                TcpControlClient client = new TcpControlClient(tcp);
                HelloMessage hello = new HelloMessage
                {
                    ProtocolVersion = ProtocolConstants.CurrentVersion,
                    ClientName = clientName,
                    ClientVersion = clientVersion,
                    Capabilities = capabilities
                };
                TcpControlServer.WriteMessage(client._stream, hello, ref client._tx);
                ProtocolFrame response = ProtocolStream.ReadFrame(client._stream);
                TcpControlServer.RequireNext(ref client._rx, response.Header.SequenceId);
                if (response.Header.Type != MessageType.HelloAck)
                    throw new ProtocolValidationException("Expected HELLO_ACK");
                using (PacketReader reader = new PacketReader(response.Payload))
                {
                    HelloAckMessage ack = HelloAckMessage.Deserialize(reader);
                    TcpControlServer.RequirePayloadEnd(reader);
                    if (ack.Status != 0 || ack.AcceptedVersion != ProtocolConstants.CurrentVersion || ack.SessionId == 0)
                        throw new ProtocolValidationException("HELLO_ACK rejected or invalid: " + ack.ErrorMessage);
                    client.SessionId = ack.SessionId;
                }
                return client;
            }
            catch
            {
                tcp.Close();
                throw;
            }
        }

        public ulong Ping(ulong timestampNs)
        {
            EnsureOpen();
            TcpControlServer.WriteMessage(_stream, new PingMessage { TimestampNs = timestampNs }, ref _tx);
            ProtocolFrame response = ProtocolStream.ReadFrame(_stream);
            TcpControlServer.RequireNext(ref _rx, response.Header.SequenceId);
            if (response.Header.Type != MessageType.Pong)
                throw new ProtocolValidationException("Expected PONG");
            return TcpControlServer.ReadPing(response.Payload).TimestampNs;
        }

        public void Shutdown(uint reasonCode, string reasonText)
        {
            EnsureOpen();
            TcpControlServer.WriteMessage(_stream, new ShutdownMessage { ReasonCode = reasonCode, ReasonText = reasonText }, ref _tx);
            Close();
        }

        private void EnsureOpen()
        {
            if (_closed) throw new ObjectDisposedException(nameof(TcpControlClient));
        }

        private void Close()
        {
            if (_closed) return;
            _closed = true;
            _stream.Dispose();
            _client.Close();
        }

        public void Dispose() { Close(); }
    }
}
