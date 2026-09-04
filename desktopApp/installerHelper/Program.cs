using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.Runtime.InteropServices;
using System.Security.Cryptography;

namespace JieXiInstaller
{
    internal static class Program
    {
        private static int Main(string[] args)
        {
            try
            {
                var localAppData = args.Length == 0
                    ? KnownFolders.LocalApplicationData()
                    : args.Length == 1
                        ? InstallerPathValidation.ValidateLocalApplicationData(args[0])
                        : throw new InvalidOperationException("Unexpected installer arguments.");
                LegacyDataMigration.Run(localAppData);
                return 0;
            }
            catch
            {
                // MSI records the non-zero result. Never disclose user paths or file contents.
                return 51;
            }
        }
    }

    public static class LegacyDataMigration
    {
        private static readonly IReadOnlyList<string> AllowedFiles = new[]
        {
            "state-v3.bin",
            "state-v3.bin.bak",
            "media-tasks-v1.json"
        };

        public static void Run(string localAppData)
        {
            if (string.IsNullOrWhiteSpace(localAppData))
                throw new InvalidOperationException("Local application data is unavailable.");

            var root = Path.GetFullPath(localAppData.Trim());
            var legacy = SafeChild(root, "解析");
            var productRoot = SafeChild(root, "JieXi");
            var target = SafeChild(productRoot, "Data");

            // This executable is scheduled only for a detected major upgrade. A
            // missing install root therefore means the user context is wrong.
            if (!TryRequireDirectory(legacy))
                throw new DirectoryNotFoundException("The detected legacy installation is unavailable.");
            using (var legacyLock = AcquireLegacyApplicationLock(legacy))
            {
                EnsureLegacyApplicationStopped();
                var sourceFiles = SnapshotLegacyFiles(legacy);
                if (sourceFiles.Count == 0) return;
                EnsureDirectory(productRoot);
                RemoveEmptyTargetDirectory(target);
                if (TryRequireDirectory(target))
                {
                    VerifyExistingTarget(legacy, target, sourceFiles);
                    return;
                }
                CommitNewDataDirectory(legacy, productRoot, target, sourceFiles);
            }
        }

        private static void CommitNewDataDirectory(
            string legacy,
            string productRoot,
            string target,
            IReadOnlyList<string> sourceFiles)
        {
            var staging = SafeChild(productRoot, ".Data.migrate-" + Guid.NewGuid().ToString("N"));
            Directory.CreateDirectory(staging);
            RejectReparsePoint(staging);
            var committedByThisInvocation = false;
            try
            {
                foreach (var fileName in sourceFiles)
                    CopyOneToStaging(legacy, staging, fileName);

                // Pre-3.1 builds did not own app.lock, so close the process race on
                // both sides of the atomic publish as tightly as possible.
                EnsureLegacyApplicationStopped();
                try
                {
                    // Same-parent directory rename publishes the complete set atomically.
                    Directory.Move(staging, target);
                    committedByThisInvocation = true;
                }
                catch (IOException)
                {
                    // A concurrent trusted invocation may have committed first.
                    if (!TryRequireDirectory(target)) throw;
                    VerifyExistingTarget(legacy, target, sourceFiles);
                }
                EnsureLegacyApplicationStopped();
            }
            catch
            {
                if (committedByThisInvocation && TryRequireDirectory(target))
                    Directory.Delete(target, true);
                throw;
            }
            finally
            {
                if (TryRequireDirectory(staging)) Directory.Delete(staging, true);
            }
        }

        private static void CopyOneToStaging(string legacy, string staging, string fileName)
        {
            if (fileName != Path.GetFileName(fileName))
                throw new InvalidOperationException("Unexpected migration file name.");

            var source = SafeChild(legacy, fileName);
            var destination = SafeChild(staging, fileName);
            RequireRegularFile(source);
            byte[] sourceHash;
            byte[] destinationHash;
            long sourceLength;
            long destinationLength;

            using (var sourceStream = new FileStream(source, FileMode.Open, FileAccess.Read, FileShare.Read))
            using (var destinationStream = new FileStream(
                destination,
                FileMode.CreateNew,
                FileAccess.ReadWrite,
                FileShare.None,
                81920,
                FileOptions.WriteThrough))
            {
                sourceStream.CopyTo(destinationStream);
                destinationStream.Flush(true);
                sourceLength = sourceStream.Length;
                destinationLength = destinationStream.Length;
                sourceStream.Position = 0;
                destinationStream.Position = 0;
                using (var sha256 = SHA256.Create()) sourceHash = sha256.ComputeHash(sourceStream);
                using (var sha256 = SHA256.Create()) destinationHash = sha256.ComputeHash(destinationStream);
            }

            if (sourceLength != destinationLength || !FixedTimeEquals(sourceHash, destinationHash))
                throw new IOException("Migration copy verification failed.");
        }

        private static void VerifyExistingTarget(
            string legacy,
            string target,
            IReadOnlyList<string> sourceFiles)
        {
            RequireDirectory(target);
            foreach (var fileName in sourceFiles)
            {
                var source = SafeChild(legacy, fileName);
                RequireRegularFile(source);
                var destination = SafeChild(target, fileName);
                RequireRegularFile(destination);
                if (!FilesMatch(source, destination))
                    throw new IOException("Existing new-store data differs from the legacy source.");
            }
        }

        private static void RemoveEmptyTargetDirectory(string target)
        {
            if (!TryRequireDirectory(target)) return;
            using (var entries = Directory.EnumerateFileSystemEntries(target).GetEnumerator())
            {
                if (entries.MoveNext()) return;
            }
            Directory.Delete(target, false);
        }

        private static void EnsureDirectory(string path)
        {
            if (!TryRequireDirectory(path)) Directory.CreateDirectory(path);
            RequireDirectory(path);
        }

        private static IReadOnlyList<string> SnapshotLegacyFiles(string legacy)
        {
            var snapshot = new List<string>();
            foreach (var fileName in AllowedFiles)
            {
                var source = SafeChild(legacy, fileName);
                if (!TryGetAttributes(source, out var attributes)) continue;
                RequireRegularFile(source, attributes);
                snapshot.Add(fileName);
            }
            return snapshot;
        }

        private static FileStream AcquireLegacyApplicationLock(string legacy)
        {
            var lockPath = SafeChild(legacy, "app.lock");
            if (TryGetAttributes(lockPath, out var attributes)) RequireRegularFile(lockPath, attributes);
            var stream = new FileStream(
                lockPath,
                FileMode.OpenOrCreate,
                FileAccess.ReadWrite,
                FileShare.ReadWrite,
                1,
                FileOptions.WriteThrough);
            try
            {
                // Matches v3.1's Java FileChannel.tryLock() range.
                stream.Lock(0, long.MaxValue);
                return stream;
            }
            catch
            {
                stream.Dispose();
                throw;
            }
        }

        private static void EnsureLegacyApplicationStopped()
        {
            foreach (var process in Process.GetProcessesByName("解析"))
            {
                using (process)
                {
                    try
                    {
                        // Versions before 3.1 had no app.lock and could be installed in a
                        // user-chosen directory, so every active exact-name process must stop.
                        if (!process.HasExited)
                            throw new IOException("The legacy application is still running.");
                    }
                    catch (InvalidOperationException)
                    {
                        // The process exited between enumeration and inspection.
                    }
                }
            }
        }

        private static bool FilesMatch(string left, string right)
        {
            var identity = ReadIdentity(left);
            return FileMatches(right, identity.Length, identity.Hash);
        }

        private static bool FileMatches(string path, long expectedLength, byte[] expectedHash)
        {
            var identity = ReadIdentity(path);
            return identity.Length == expectedLength && FixedTimeEquals(identity.Hash, expectedHash);
        }

        private static FileIdentity ReadIdentity(string path)
        {
            using (var stream = new FileStream(path, FileMode.Open, FileAccess.Read, FileShare.Read))
            using (var sha256 = SHA256.Create())
                return new FileIdentity(stream.Length, sha256.ComputeHash(stream));
        }

        private static string SafeChild(string parent, string name)
        {
            var normalizedParent = Path.GetFullPath(parent).TrimEnd(Path.DirectorySeparatorChar, Path.AltDirectorySeparatorChar);
            var candidate = Path.GetFullPath(Path.Combine(normalizedParent, name));
            var prefix = normalizedParent + Path.DirectorySeparatorChar;
            if (!candidate.StartsWith(prefix, StringComparison.OrdinalIgnoreCase))
                throw new InvalidOperationException("Migration path escaped its root.");
            return candidate;
        }

        private static void RejectReparsePoint(string path)
        {
            if ((File.GetAttributes(path) & FileAttributes.ReparsePoint) != 0)
                throw new IOException("Migration paths must not be reparse points.");
        }

        private static bool TryRequireDirectory(string path)
        {
            if (!TryGetAttributes(path, out var attributes)) return false;
            RequireDirectory(path, attributes);
            return true;
        }

        private static bool TryGetAttributes(string path, out FileAttributes attributes)
        {
            try
            {
                attributes = File.GetAttributes(path);
                return true;
            }
            catch (FileNotFoundException)
            {
                attributes = 0;
                return false;
            }
            catch (DirectoryNotFoundException)
            {
                attributes = 0;
                return false;
            }
        }

        private static void RequireDirectory(string path)
        {
            RequireDirectory(path, File.GetAttributes(path));
        }

        private static void RequireDirectory(string path, FileAttributes attributes)
        {
            if ((attributes & FileAttributes.Directory) == 0)
                throw new IOException("A migration directory path is not a directory.");
            if ((attributes & FileAttributes.ReparsePoint) != 0)
                throw new IOException("Migration paths must not be reparse points.");
        }

        private static void RequireRegularFile(string path)
        {
            RequireRegularFile(path, File.GetAttributes(path));
        }

        private static void RequireRegularFile(string path, FileAttributes attributes)
        {
            if ((attributes & FileAttributes.Directory) != 0)
                throw new IOException("A migration file path is not a regular file.");
            if ((attributes & FileAttributes.ReparsePoint) != 0)
                throw new IOException("Migration paths must not be reparse points.");
        }

        private static bool FixedTimeEquals(byte[] left, byte[] right)
        {
            if (left.Length != right.Length) return false;
            var difference = 0;
            for (var index = 0; index < left.Length; index++)
                difference |= left[index] ^ right[index];
            return difference == 0;
        }

        private sealed class FileIdentity
        {
            public FileIdentity(long length, byte[] hash)
            {
                Length = length;
                Hash = hash;
            }

            public long Length { get; }
            public byte[] Hash { get; }
        }
    }

    public static class InstallerPathValidation
    {
        public static string ValidateLocalApplicationData(string value)
        {
            if (string.IsNullOrWhiteSpace(value) || !Path.IsPathRooted(value))
                throw new InvalidOperationException("The installer local-data path is invalid.");

            var full = Path.GetFullPath(value.Trim())
                .TrimEnd(Path.DirectorySeparatorChar, Path.AltDirectorySeparatorChar);
            var directory = new DirectoryInfo(full);
            RequirePlainDirectory(directory);

            var processKnownFolder = Path.GetFullPath(KnownFolders.LocalApplicationData())
                .TrimEnd(Path.DirectorySeparatorChar, Path.AltDirectorySeparatorChar);
            if (full.Equals(processKnownFolder, StringComparison.OrdinalIgnoreCase)) return full;

            var appData = directory.Parent;
            var profile = appData?.Parent;
            if (!directory.Name.Equals("Local", StringComparison.OrdinalIgnoreCase) ||
                appData == null || !appData.Name.Equals("AppData", StringComparison.OrdinalIgnoreCase) ||
                profile == null)
                throw new InvalidOperationException("The installer path is not a user LocalAppData directory.");
            RequirePlainDirectory(appData);
            RequirePlainDirectory(profile);
            return full;
        }

        private static void RequirePlainDirectory(DirectoryInfo directory)
        {
            var attributes = File.GetAttributes(directory.FullName);
            if ((attributes & FileAttributes.Directory) == 0 ||
                (attributes & FileAttributes.ReparsePoint) != 0)
                throw new IOException("Installer data paths must be plain directories.");
        }
    }

    internal static class KnownFolders
    {
        private static readonly Guid LocalApplicationDataId =
            new Guid("F1B32785-6FBA-4FCF-9D55-7B8E7F157091");

        public static string LocalApplicationData()
        {
            var folderId = LocalApplicationDataId;
            var result = SHGetKnownFolderPath(ref folderId, 0, IntPtr.Zero, out var pathPointer);
            if (result != 0) Marshal.ThrowExceptionForHR(result);
            try
            {
                return Marshal.PtrToStringUni(pathPointer)
                    ?? throw new InvalidOperationException("Local application data is unavailable.");
            }
            finally
            {
                Marshal.FreeCoTaskMem(pathPointer);
            }
        }

        [DllImport("shell32.dll", CharSet = CharSet.Unicode)]
        private static extern int SHGetKnownFolderPath(
            ref Guid folderId,
            uint flags,
            IntPtr accessToken,
            out IntPtr path);
    }
}
