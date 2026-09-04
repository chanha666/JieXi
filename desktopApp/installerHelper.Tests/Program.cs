using System;
using System.IO;
using System.Linq;
using System.Threading;
using JieXiInstaller;

namespace JieXiInstaller.Tests
{
    internal static class Program
    {
        private static int Main(string[] args)
        {
            var parent = args.Length == 1 ? Path.GetFullPath(args[0]) : Path.GetTempPath();
            Directory.CreateDirectory(parent);
            var root = Path.Combine(parent, "migration-test-" + Guid.NewGuid().ToString("N"));
            Directory.CreateDirectory(root);
            try
            {
                CopiesOnlyTheWhitelist(root);
                NeverOverwritesNewData(root);
                AcceptsAnIdenticalExistingDestination(root);
                RejectsAConflictingExistingDestination(root);
                ActiveLegacyLockStopsMigration(root);
                SecondSourceFailureLeavesNoPartialCommit(root);
                RejectsNonRegularSource(root);
                SourceDisappearingAfterSnapshotFailsClosed(root);
                AcceptsAPlainLocalAppDataInstallerPath(root);
                AcceptsMsiSafeDotSuffixedLocalAppDataPath(root);
                RejectsAnArbitraryInstallerPath(root);
                LockedSourceCannotLeaveAPartialFile(root);
                Console.WriteLine("Installer migration helper tests passed: 12");
                return 0;
            }
            catch (Exception error)
            {
                Console.Error.WriteLine(error.GetType().Name + ": " + error.Message);
                return 1;
            }
            finally
            {
                if (Directory.Exists(root)) Directory.Delete(root, true);
            }
        }

        private static void CopiesOnlyTheWhitelist(string root)
        {
            var caseRoot = NewCase(root, "whitelist");
            var legacy = Directory.CreateDirectory(Path.Combine(caseRoot, "解析")).FullName;
            var expected = new[] { "state-v3.bin", "state-v3.bin.bak", "media-tasks-v1.json" };
            for (var index = 0; index < expected.Length; index++)
                File.WriteAllBytes(Path.Combine(legacy, expected[index]), new byte[] { (byte)(index + 1), 9, 7 });
            File.WriteAllText(Path.Combine(legacy, "解析.exe"), "must not migrate");

            LegacyDataMigration.Run(caseRoot);

            var target = Path.Combine(caseRoot, "JieXi", "Data");
            foreach (var name in expected)
            {
                Assert(File.Exists(Path.Combine(target, name)), "Missing migrated file: " + name);
                Assert(File.Exists(Path.Combine(legacy, name)), "Legacy recovery copy was deleted: " + name);
            }
            Assert(!File.Exists(Path.Combine(target, "解析.exe")), "An unrelated installation file was copied.");
            Assert(Directory.GetFiles(target).All(path => expected.Contains(Path.GetFileName(path))), "Unexpected file in data directory.");
        }

        private static void NeverOverwritesNewData(string root)
        {
            var caseRoot = NewCase(root, "no-overwrite");
            var legacy = Directory.CreateDirectory(Path.Combine(caseRoot, "解析")).FullName;
            var target = Directory.CreateDirectory(Path.Combine(caseRoot, "JieXi", "Data")).FullName;
            File.WriteAllText(Path.Combine(legacy, "state-v3.bin"), "legacy");
            File.WriteAllText(Path.Combine(target, "state-v3.bin"), "current");
            var failed = false;

            try { LegacyDataMigration.Run(caseRoot); }
            catch (IOException) { failed = true; }

            Assert(failed, "Conflicting data did not stop the migration.");
            Assert(File.ReadAllText(Path.Combine(legacy, "state-v3.bin")) == "legacy", "Legacy data was changed.");
            Assert(File.ReadAllText(Path.Combine(target, "state-v3.bin")) == "current", "New data was overwritten.");
        }

        private static void LockedSourceCannotLeaveAPartialFile(string root)
        {
            var caseRoot = NewCase(root, "locked-source");
            var legacy = Directory.CreateDirectory(Path.Combine(caseRoot, "解析")).FullName;
            var source = Path.Combine(legacy, "media-tasks-v1.json");
            File.WriteAllText(source, "locked");
            var failed = false;
            using (new FileStream(source, FileMode.Open, FileAccess.ReadWrite, FileShare.None))
            {
                try { LegacyDataMigration.Run(caseRoot); }
                catch (IOException) { failed = true; }
            }

            var target = Path.Combine(caseRoot, "JieXi", "Data");
            Assert(failed, "A locked source did not fail closed.");
            Assert(!File.Exists(Path.Combine(target, "media-tasks-v1.json")), "A partial destination was published.");
            Assert(!Directory.Exists(target), "A partial data directory was published.");
            Assert(File.Exists(source), "The only complete source was deleted.");
        }

        private static void SecondSourceFailureLeavesNoPartialCommit(string root)
        {
            var caseRoot = NewCase(root, "second-source-failure");
            var legacy = Directory.CreateDirectory(Path.Combine(caseRoot, "解析")).FullName;
            var first = Path.Combine(legacy, "state-v3.bin");
            var second = Path.Combine(legacy, "media-tasks-v1.json");
            File.WriteAllText(first, "first complete file");
            File.WriteAllText(second, "second complete file");
            var failed = false;

            using (new FileStream(second, FileMode.Open, FileAccess.ReadWrite, FileShare.None))
            {
                try { LegacyDataMigration.Run(caseRoot); }
                catch (IOException) { failed = true; }
            }

            var target = Path.Combine(caseRoot, "JieXi", "Data");
            Assert(failed, "A locked second source did not fail closed.");
            Assert(!Directory.Exists(target), "The first file was partially committed.");
            Assert(File.ReadAllText(first) == "first complete file", "The first source was changed.");
            Assert(File.ReadAllText(second) == "second complete file", "The second source was changed.");

            LegacyDataMigration.Run(caseRoot);
            Assert(File.ReadAllText(Path.Combine(target, "state-v3.bin")) == "first complete file",
                "The first file did not migrate after retry.");
            Assert(File.ReadAllText(Path.Combine(target, "media-tasks-v1.json")) == "second complete file",
                "The second file did not migrate after retry.");
        }

        private static void RejectsNonRegularSource(string root)
        {
            var caseRoot = NewCase(root, "non-regular-source");
            var legacy = Directory.CreateDirectory(Path.Combine(caseRoot, "解析")).FullName;
            Directory.CreateDirectory(Path.Combine(legacy, "state-v3.bin"));
            var failed = false;

            try { LegacyDataMigration.Run(caseRoot); }
            catch (IOException) { failed = true; }

            Assert(failed, "A directory masquerading as state-v3.bin was treated as absent.");
            Assert(!Directory.Exists(Path.Combine(caseRoot, "JieXi", "Data")),
                "Invalid source data produced a destination directory.");
        }

        private static void SourceDisappearingAfterSnapshotFailsClosed(string root)
        {
            var caseRoot = NewCase(root, "source-disappears");
            var legacy = Directory.CreateDirectory(Path.Combine(caseRoot, "解析")).FullName;
            var productRoot = Directory.CreateDirectory(Path.Combine(caseRoot, "JieXi")).FullName;
            var first = Path.Combine(legacy, "state-v3.bin");
            var second = Path.Combine(legacy, "state-v3.bin.bak");
            using (var large = new FileStream(first, FileMode.CreateNew, FileAccess.Write, FileShare.None))
                large.SetLength(32L * 1024L * 1024L);
            File.WriteAllText(second, "must not disappear silently");
            var removed = new ManualResetEventSlim(false);
            var failed = false;

            using (var watcher = new FileSystemWatcher(productRoot, "state-v3.bin"))
            {
                watcher.IncludeSubdirectories = true;
                watcher.Created += (_, __) =>
                {
                    try { File.Delete(second); }
                    finally { removed.Set(); }
                };
                watcher.EnableRaisingEvents = true;
                try { LegacyDataMigration.Run(caseRoot); }
                catch (FileNotFoundException) { failed = true; }
                catch (DirectoryNotFoundException) { failed = true; }
            }

            Assert(removed.Wait(2000), "The disappearance test did not observe staging creation.");
            Assert(failed, "A source disappearing after the snapshot was treated as absent.");
            Assert(!Directory.Exists(Path.Combine(productRoot, "Data")),
                "A source disappearance published a partial data directory.");
            Assert(File.Exists(first), "The remaining complete source was deleted.");
        }

        private static void AcceptsAPlainLocalAppDataInstallerPath(string root)
        {
            var local = Directory.CreateDirectory(Path.Combine(root, "profile", "AppData", "Local")).FullName;
            var validated = InstallerPathValidation.ValidateLocalApplicationData(local + Path.DirectorySeparatorChar);
            Assert(validated == local, "A plain profile AppData\\Local path was not normalized correctly.");
        }

        private static void RejectsAnArbitraryInstallerPath(string root)
        {
            var arbitrary = Directory.CreateDirectory(Path.Combine(root, "arbitrary")).FullName;
            var failed = false;
            try { InstallerPathValidation.ValidateLocalApplicationData(arbitrary); }
            catch (InvalidOperationException) { failed = true; }
            Assert(failed, "An arbitrary installer-controlled path was accepted.");
        }

        private static void AcceptsMsiSafeDotSuffixedLocalAppDataPath(string root)
        {
            var local = Directory.CreateDirectory(Path.Combine(root, "msi-profile", "AppData", "Local")).FullName;
            var validated = InstallerPathValidation.ValidateLocalApplicationData(
                local + Path.DirectorySeparatorChar + ".");
            Assert(validated == local, "The MSI-safe AppData\\Local\\. path was not normalized correctly.");
        }

        private static void AcceptsAnIdenticalExistingDestination(string root)
        {
            var caseRoot = NewCase(root, "identical-existing");
            var legacy = Directory.CreateDirectory(Path.Combine(caseRoot, "解析")).FullName;
            var target = Directory.CreateDirectory(Path.Combine(caseRoot, "JieXi", "Data")).FullName;
            var bytes = new byte[] { 4, 0, 0, 7 };
            File.WriteAllBytes(Path.Combine(legacy, "state-v3.bin.bak"), bytes);
            File.WriteAllBytes(Path.Combine(target, "state-v3.bin.bak"), bytes);

            LegacyDataMigration.Run(caseRoot);

            Assert(File.ReadAllBytes(Path.Combine(target, "state-v3.bin.bak")).SequenceEqual(bytes),
                "An identical destination was changed.");
        }

        private static void ActiveLegacyLockStopsMigration(string root)
        {
            var caseRoot = NewCase(root, "active-legacy-lock");
            var legacy = Directory.CreateDirectory(Path.Combine(caseRoot, "解析")).FullName;
            var source = Path.Combine(legacy, "state-v3.bin");
            var lockPath = Path.Combine(legacy, "app.lock");
            File.WriteAllText(source, "complete state");
            var failed = false;

            using (var activeLock = new FileStream(
                lockPath, FileMode.OpenOrCreate, FileAccess.ReadWrite, FileShare.ReadWrite))
            {
                activeLock.Lock(0, long.MaxValue);
                try { LegacyDataMigration.Run(caseRoot); }
                catch (IOException) { failed = true; }
                finally { activeLock.Unlock(0, long.MaxValue); }
            }

            var destination = Path.Combine(caseRoot, "JieXi", "Data", "state-v3.bin");
            Assert(failed, "An active v3.1 app.lock did not stop migration.");
            Assert(!File.Exists(destination), "Migration published data while the legacy app lock was active.");
            Assert(File.ReadAllText(source) == "complete state", "Locked legacy state was changed.");

            LegacyDataMigration.Run(caseRoot);
            Assert(File.ReadAllText(destination) == "complete state", "Migration did not recover after the app lock was released.");
        }

        private static void RejectsAConflictingExistingDestination(string root)
        {
            var caseRoot = NewCase(root, "conflicting-existing");
            var legacy = Directory.CreateDirectory(Path.Combine(caseRoot, "解析")).FullName;
            var target = Directory.CreateDirectory(Path.Combine(caseRoot, "JieXi", "Data")).FullName;
            var source = Path.Combine(legacy, "media-tasks-v1.json");
            var destination = Path.Combine(target, "media-tasks-v1.json");
            File.WriteAllText(source, "complete legacy data");
            File.WriteAllText(destination, "different new data");
            var failed = false;

            try { LegacyDataMigration.Run(caseRoot); }
            catch (IOException) { failed = true; }

            Assert(failed, "Conflicting data did not fail closed.");
            Assert(File.ReadAllText(source) == "complete legacy data", "The complete legacy source was changed.");
            Assert(File.ReadAllText(destination) == "different new data", "The conflicting destination was overwritten.");
        }

        private static string NewCase(string root, string name)
        {
            var path = Path.Combine(root, name);
            Directory.CreateDirectory(path);
            return path;
        }

        private static void Assert(bool condition, string message)
        {
            if (!condition) throw new InvalidOperationException(message);
        }
    }
}
