using System;
using System.IO;
using System.IO.MemoryMappedFiles;
using System.Security.Cryptography;
using System.Threading;
using System.Diagnostics;

namespace MinecraftBridge.Framebuffer
{
    internal sealed class SharedFramebuffer : IDisposable
    {
        public const int MappingHeaderSize = 128;
        public const int SlotHeaderSize = 128;
        public const int SlotCount = 3;
        public const int Width = 3840;
        public const int Height = 2160;
        public const int BytesPerPixel = 4;
        public const int SlotCapacity = Width * Height * BytesPerPixel;
        public const long SlotStride = SlotHeaderSize + (long)SlotCapacity;
        public const long MappingSize = MappingHeaderSize + SlotCount * SlotStride;
        public const uint Magic = 0x5342434d;
        public const ushort Version = 1;
        public const uint PixelFormatBgra8 = 2;

        private const int StateFree = 0;
        private const int StateWriting = 1;
        private const int StateReady = 2;
        private const int StateReading = 3;

        private readonly FileStream file;
        private readonly MemoryMappedFile mapping;
        private readonly MemoryMappedViewAccessor view;
        private ulong nextSequence;
        private unsafe byte* basePointer;
        private bool pointerAcquired;
        private bool disposed;

        private SharedFramebuffer(FileStream file, MemoryMappedFile mapping, MemoryMappedViewAccessor view)
        {
            this.file = file; this.mapping = mapping; this.view = view;
            unsafe { view.SafeMemoryMappedViewHandle.AcquirePointer(ref basePointer); pointerAcquired = true; }
        }

        public static SharedFramebuffer CreateNew(string path, uint sessionId)
        {
            if (sessionId == 0) throw new ArgumentOutOfRangeException(nameof(sessionId));
            if (string.IsNullOrEmpty(path)) throw new ArgumentException("Mapping path is required", nameof(path));
            FileStream file = new FileStream(path, FileMode.CreateNew, FileAccess.ReadWrite, FileShare.ReadWrite);
            MemoryMappedFile mapping = null;
            MemoryMappedViewAccessor view = null;
            SharedFramebuffer result = null;
            try
            {
                file.SetLength(MappingSize);
                mapping = MemoryMappedFile.CreateFromFile(file, null, MappingSize, MemoryMappedFileAccess.ReadWrite,
                    HandleInheritability.None, true);
                view = mapping.CreateViewAccessor(0, MappingSize, MemoryMappedFileAccess.ReadWrite);
                result = new SharedFramebuffer(file, mapping, view);
                byte[] generation = new byte[16];
                using (var rng = RandomNumberGenerator.Create()) rng.GetBytes(generation);
                ulong generationHi = BitConverter.ToUInt64(generation, 0);
                ulong generationLo = BitConverter.ToUInt64(generation, 8);
                if (generationHi == 0 && generationLo == 0) generationLo = 1;
                result.Initialize(sessionId, generationHi, generationLo);
                return result;
            }
            catch
            {
                if (result != null) result.Dispose();
                else
                {
                    if (view != null) view.Dispose();
                    if (mapping != null) mapping.Dispose();
                    file.Dispose();
                }
                try { File.Delete(path); } catch { }
                throw;
            }
        }

        public static SharedFramebuffer OpenExisting(string path)
        {
            FileStream file = new FileStream(path, FileMode.Open, FileAccess.ReadWrite, FileShare.ReadWrite);
            MemoryMappedFile mapping = null;
            MemoryMappedViewAccessor view = null;
            SharedFramebuffer result = null;
            try
            {
                if (file.Length != MappingSize) throw new InvalidDataException("Shared framebuffer size mismatch");
                mapping = MemoryMappedFile.CreateFromFile(file, null, MappingSize, MemoryMappedFileAccess.ReadWrite,
                    HandleInheritability.None, true);
                view = mapping.CreateViewAccessor(0, MappingSize, MemoryMappedFileAccess.ReadWrite);
                result = new SharedFramebuffer(file, mapping, view);
                result.ValidateHeader();
                result.InitializeNextSequence();
                return result;
            }
            catch
            {
                if (result != null) result.Dispose();
                else
                {
                    if (view != null) view.Dispose();
                    if (mapping != null) mapping.Dispose();
                    file.Dispose();
                }
                throw;
            }
        }

        public uint SessionId => ReadU32(24);
        public ulong GenerationHi => ReadU64(32);
        public ulong GenerationLo => ReadU64(40);

        public int TryAcquireFreeSlot()
        {
            for (int i = 0; i < SlotCount; i++)
                if (CompareState(i, StateFree, StateWriting)) return i;
            return -1;
        }

        public ulong Publish(int slot, int width, int height, int stride, byte[] payload)
        {
            ValidateSlot(slot); ValidateMetadata(width, height, stride, payload.Length);
            ulong sequence = checked(nextSequence + 1UL);
            long o = SlotOffset(slot);
            WriteU64(o + 8, sequence); WriteU64(o + 16, GenerationHi); WriteU64(o + 24, GenerationLo);
            WriteU32(o + 32, (uint)width); WriteU32(o + 36, (uint)height); WriteU32(o + 40, (uint)stride);
            WriteU32(o + 44, PixelFormatBgra8); WriteU32(o + 48, (uint)payload.Length); WriteU32(o + 52, 0);
            WriteU64(o + 56, (ulong)(Stopwatch.GetTimestamp() * (1_000_000_000.0 / Stopwatch.Frequency)));
            WriteU64(o + 64, Fnv1a64(payload, 0, payload.Length));
            CopyToMapping(o + SlotHeaderSize, payload);
            if (!CompareState(slot, StateWriting, StateReady))
                throw new InvalidOperationException("Slot was no longer WRITING during publication");
            nextSequence = sequence;
            return sequence;
        }

        public int TryAcquireLatestReady(out ulong sequence)
        {
            sequence = 0;
            while (true)
            {
                int best = -1;
                ulong bestSequence = 0;
                for (int i = 0; i < SlotCount; i++)
                {
                    if (ReadStateAcquire(i) != StateReady) continue;
                    ulong candidate = ReadU64(SlotOffset(i) + 8);
                    if (best < 0 || candidate > bestSequence) { best = i; bestSequence = candidate; }
                }
                if (best < 0) return -1;
                if (!CompareState(best, StateReady, StateReading)) continue;

                bool valid = ValidateMetadataFromMapping(best)
                    && ReadU64(SlotOffset(best) + 16) == GenerationHi
                    && ReadU64(SlotOffset(best) + 24) == GenerationLo
                    && ((ReadU32(20) & 1U) == 0 || ValidateChecksum(best));
                if (!valid)
                {
                    CompareState(best, StateReading, StateFree);
                    continue;
                }

                sequence = bestSequence;
                return best;
            }
        }

        public void ReleaseReading(int slot)
        {
            ValidateSlot(slot);
            if (!CompareState(slot, StateReading, StateFree))
                throw new InvalidOperationException("Slot was not READING");
        }

        public void DrainStaleReady(ulong newestSequence)
        {
            for (int i = 0; i < SlotCount; i++)
            {
                if (ReadStateAcquire(i) != StateReady) continue;
                if (ReadU64(SlotOffset(i) + 8) >= newestSequence) continue;
                if (CompareState(i, StateReady, StateReading)) CompareState(i, StateReading, StateFree);
            }
        }

        public FrameMetadata GetMetadata(int slot)
        {
            ValidateSlot(slot);
            if (ReadStateAcquire(slot) != StateReading)
                throw new InvalidOperationException("Metadata can only be read from an owned READING slot");
            long o = SlotOffset(slot);
            return new FrameMetadata(ReadU64(o + 8), ReadU64(o + 56), (int)ReadU32(o + 32),
                (int)ReadU32(o + 36), (int)ReadU32(o + 40), ReadU32(o + 44), ReadU32(o + 48), ReadU64(o + 64));
        }

        public byte[] ReadPayload(int slot)
        {
            ValidateSlot(slot);
            if (ReadStateAcquire(slot) != StateReading)
                throw new InvalidOperationException("Payload can only be read from an owned READING slot");
            long o = SlotOffset(slot);
            uint length = ReadU32(o + 48);
            if (length > SlotCapacity) throw new InvalidDataException("Invalid payload length");
            var data = new byte[length];
            CopyFromMapping(o + SlotHeaderSize, data);
            return data;
        }

        public bool TryReadLatestFrame(out FrameMetadata metadata, out byte[] payload)
        {
            metadata = null;
            payload = null;
            int slot = TryAcquireLatestReady(out ulong sequence);
            if (slot < 0) return false;
            try
            {
                metadata = GetMetadata(slot);
                payload = ReadPayload(slot);
                if (metadata.Sequence != sequence)
                    throw new InvalidDataException("Frame sequence changed while reading");
                return true;
            }
            finally
            {
                ReleaseReading(slot);
            }
        }

        private void Initialize(uint sessionId, ulong generationHi, ulong generationLo)
        {
            WriteU32(0, Magic); WriteU16(4, Version); WriteU16(6, MappingHeaderSize); WriteU32(8, SlotCount);
            WriteU32(12, (uint)SlotStride); WriteU32(16, SlotCapacity); WriteU32(20, 1); WriteU32(24, sessionId);
            WriteU64(32, generationHi); WriteU64(40, generationLo);
            for (int i = 0; i < SlotCount; i++) WriteU32(SlotOffset(i), StateFree);
            view.Flush();
        }

        private void InitializeNextSequence()
        {
            ulong max = 0;
            for (int i = 0; i < SlotCount; i++)
            {
                int state = ReadStateAcquire(i);
                if (state == StateReady || state == StateReading)
                {
                    ulong sequence = ReadU64(SlotOffset(i) + 8);
                    if (sequence > max) max = sequence;
                }
            }
            nextSequence = max;
        }

        private void ValidateHeader()
        {
            if (ReadU32(0) != Magic || ReadU16(4) != Version || ReadU16(6) != MappingHeaderSize ||
                ReadU32(8) != SlotCount || ReadU32(12) != SlotStride || ReadU32(16) != SlotCapacity)
                throw new InvalidDataException("Invalid shared framebuffer header");
            if (ReadU32(24) == 0) throw new InvalidDataException("Missing mapping session ID");
            if (GenerationHi == 0 && GenerationLo == 0) throw new InvalidDataException("Missing mapping generation");
            if (ReadU32(28) != 0 || ReadU64(56) != 0 || !IsZero(64, 64))
                throw new InvalidDataException("Non-zero reserved mapping header bytes");
        }

        private unsafe bool IsZero(long offset, int length)
        {
            for (int i = 0; i < length; i++) if (*(basePointer + offset + i) != 0) return false;
            return true;
        }

        private unsafe bool ValidateChecksum(int slot)
        {
            long o = SlotOffset(slot);
            uint length = ReadU32(o + 48);
            ulong expected = ReadU64(o + 64);
            ulong actual = 14695981039346656037UL;
            for (uint i = 0; i < length; i++) { actual ^= *(basePointer + o + SlotHeaderSize + i); actual *= 1099511628211UL; }
            return actual == expected;
        }

        private bool ValidateMetadataFromMapping(int slot)
        {
            long o = SlotOffset(slot);
            uint width = ReadU32(o + 32), height = ReadU32(o + 36), stride = ReadU32(o + 40), format = ReadU32(o + 44), length = ReadU32(o + 48);
            if (ReadU32(o + 4) != 0 || ReadU32(o + 52) != 0 || !IsZero(o + 72, 56)) return false;
            if (width < 1 || width > Width || height < 1 || height > Height || format != PixelFormatBgra8 || stride < width * 4 || stride > SlotCapacity || length > SlotCapacity) return false;
            ulong required = ((ulong)height - 1UL) * stride + (ulong)width * 4UL;
            return required <= length;
        }

        private static void ValidateMetadata(int width, int height, int stride, int length)
        {
            if (width < 1 || width > Width || height < 1 || height > Height || stride < width * 4 || stride > SlotCapacity || length > SlotCapacity)
                throw new ArgumentOutOfRangeException();
            ulong required = ((ulong)height - 1UL) * (uint)stride + (uint)width * 4UL;
            if (required > (ulong)length) throw new ArgumentException("Payload is smaller than the required frame footprint");
        }

        private long SlotOffset(int slot) { ValidateSlot(slot); return MappingHeaderSize + slot * SlotStride; }
        private static void ValidateSlot(int slot) { if (slot < 0 || slot >= SlotCount) throw new ArgumentOutOfRangeException(nameof(slot)); }

        private unsafe int ReadStateAcquire(int slot) { return Volatile.Read(ref *(int*)(basePointer + SlotOffset(slot))); }
        private unsafe bool CompareState(int slot, int expected, int next)
        {
            return Interlocked.CompareExchange(ref *(int*)(basePointer + SlotOffset(slot)), next, expected) == expected;
        }

        private unsafe uint ReadU32(long offset) { return *(uint*)(basePointer + offset); }
        private unsafe ushort ReadU16(long offset) { return *(ushort*)(basePointer + offset); }
        private unsafe ulong ReadU64(long offset) { return *(ulong*)(basePointer + offset); }
        private unsafe void WriteU32(long offset, uint value) { *(uint*)(basePointer + offset) = value; }
        private unsafe void WriteU16(long offset, ushort value) { *(ushort*)(basePointer + offset) = value; }
        private unsafe void WriteU64(long offset, ulong value) { *(ulong*)(basePointer + offset) = value; }
        private unsafe void CopyToMapping(long offset, byte[] data) { for (int i = 0; i < data.Length; i++) *(basePointer + offset + i) = data[i]; }
        private unsafe void CopyFromMapping(long offset, byte[] data) { for (int i = 0; i < data.Length; i++) data[i] = *(basePointer + offset + i); }

        public sealed class FrameMetadata
        {
            public ulong Sequence { get; private set; }
            public ulong TimestampNs { get; private set; }
            public int Width { get; private set; }
            public int Height { get; private set; }
            public int Stride { get; private set; }
            public uint PixelFormat { get; private set; }
            public uint PayloadLength { get; private set; }
            public ulong Checksum { get; private set; }

            internal FrameMetadata(ulong sequence, ulong timestampNs, int width, int height, int stride,
                uint pixelFormat, uint payloadLength, ulong checksum)
            {
                Sequence = sequence; TimestampNs = timestampNs; Width = width; Height = height;
                Stride = stride; PixelFormat = pixelFormat; PayloadLength = payloadLength; Checksum = checksum;
            }
        }

        private static ulong Fnv1a64(byte[] data, int offset, int count)
        {
            ulong hash = 14695981039346656037UL;
            for (int i = 0; i < count; i++) { hash ^= data[offset + i]; hash *= 1099511628211UL; }
            return hash;
        }

        public void Dispose()
        {
            if (disposed) return;
            disposed = true;
            if (pointerAcquired) { view.SafeMemoryMappedViewHandle.ReleasePointer(); pointerAcquired = false; }
            view.Dispose(); mapping.Dispose(); file.Dispose();
        }
    }
}
