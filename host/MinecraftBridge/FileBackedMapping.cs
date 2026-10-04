using System;
using System.IO;
using System.IO.MemoryMappedFiles;

namespace MinecraftBridge
{
    /// <summary>Owns a file-backed mapping without imposing framebuffer or slot semantics.</summary>
    internal sealed class FileBackedMapping : IDisposable
    {
        private readonly FileStream _file;
        private readonly MemoryMappedFile _mapping;
        private readonly MemoryMappedViewAccessor _view;

        public long Length { get; private set; }

        private FileBackedMapping(FileStream file, long length)
        {
            _file = file;
            Length = length;
            _mapping = MemoryMappedFile.CreateFromFile(file, null, length, MemoryMappedFileAccess.ReadWrite,
                HandleInheritability.None, true);
            _view = _mapping.CreateViewAccessor(0, length, MemoryMappedFileAccess.ReadWrite);
        }

        public static FileBackedMapping CreateNew(string path, long length)
        {
            ValidateArguments(path, length);
            FileStream file = new FileStream(path, FileMode.CreateNew, FileAccess.ReadWrite, FileShare.ReadWrite);
            try
            {
                file.SetLength(length);
                return new FileBackedMapping(file, length);
            }
            catch
            {
                file.Dispose();
                throw;
            }
        }

        public static FileBackedMapping OpenExisting(string path, long expectedLength)
        {
            ValidateArguments(path, expectedLength);
            FileStream file = new FileStream(path, FileMode.Open, FileAccess.ReadWrite, FileShare.ReadWrite);
            try
            {
                if (file.Length != expectedLength)
                    throw new InvalidDataException("Mapping size mismatch: expected " + expectedLength + ", got " + file.Length);
                return new FileBackedMapping(file, expectedLength);
            }
            catch
            {
                file.Dispose();
                throw;
            }
        }

        public void Read(long offset, byte[] buffer, int bufferOffset, int count)
        {
            ValidateRange(offset, count);
            _view.ReadArray(offset, buffer, bufferOffset, count);
        }

        public void Write(long offset, byte[] buffer, int bufferOffset, int count)
        {
            ValidateRange(offset, count);
            _view.WriteArray(offset, buffer, bufferOffset, count);
        }

        public void Flush() { _view.Flush(); }

        private void ValidateRange(long offset, int count)
        {
            if (offset < 0 || count < 0 || offset > Length - count)
                throw new ArgumentOutOfRangeException(nameof(offset), "Mapping access is outside its declared size");
        }

        private static void ValidateArguments(string path, long length)
        {
            if (string.IsNullOrEmpty(path)) throw new ArgumentException("A mapping path is required", nameof(path));
            if (length <= 0) throw new ArgumentOutOfRangeException(nameof(length));
        }

        public void Dispose()
        {
            _view.Dispose();
            _mapping.Dispose();
            _file.Dispose();
        }
    }
}
