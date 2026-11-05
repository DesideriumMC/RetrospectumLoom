package net.fabricmc.loom.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import static org.junit.Assert.*;

public class CacheLockTest {
    @Test public void independentProcessesCannotInterleaveTheirCriticalSections() throws Exception {
        Path directory = Files.createTempDirectory("retrospectum-lock");
        Path lock = directory.resolve("locks");
        Path target = directory.resolve("artifact.bin");
        Path events = directory.resolve("events.txt");
        String java = Paths.get(System.getProperty("java.home"), "bin", "java").toString();
        String classpath = Paths.get(CacheLock.class.getProtectionDomain().getCodeSource().getLocation().toURI())
            + File.pathSeparator + Paths.get(CacheLockProcess.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        Process first = new ProcessBuilder(java, "-cp", classpath, CacheLockProcess.class.getName(),
            lock.toString(), events.toString(), "first", target.toString()).inheritIO().start();
        Process second = null;
        try {
            second = new ProcessBuilder(java, "-cp", classpath, CacheLockProcess.class.getName(),
                lock.toString(), events.toString(), "second", directory.resolve("unused/../artifact.bin").toString()).inheritIO().start();
            assertTrue(first.waitFor(15, TimeUnit.SECONDS));
            assertTrue(second.waitFor(15, TimeUnit.SECONDS));
            assertEquals(0, first.exitValue());
            assertEquals(0, second.exitValue());
            List<String> lines = Files.readAllLines(events);
            assertEquals(4, lines.size());
            assertEquals("begin", lines.get(0).split(" ")[0]);
            assertEquals("end " + lines.get(0).split(" ")[1], lines.get(1));
            assertEquals("begin", lines.get(2).split(" ")[0]);
            assertEquals("end " + lines.get(2).split(" ")[1], lines.get(3));
            assertNotEquals(lines.get(0), lines.get(2));
            assertTrue(Files.isRegularFile(CacheLock.lockFile(lock, target)));
            assertFalse(Files.exists(target.resolveSibling("artifact.bin.lock")));
        } finally {
            first.destroyForcibly();
            if (second != null) second.destroyForcibly();
        }
    }

    @Test public void nestedOperationsShareOneLockAndTargetIdentityIsStableAfterCreation() throws Exception {
        Path directory = Files.createTempDirectory("retrospectum-lock-nested");
        Path locks = directory.resolve("locks");
        Path target = directory.resolve("artifact.bin");
        Path expected = CacheLock.lockFile(locks, target);
        assertEquals(expected, CacheLock.lockFile(locks, directory.resolve("unused/../artifact.bin")));
        assertNotEquals(expected, CacheLock.lockFile(locks, directory.resolve("other.bin")));
        if (target.getFileSystem().getSeparator().equals("\\"))
            assertEquals(expected, CacheLock.lockFile(locks, directory.resolve("ARTIFACT.BIN")));
        assertEquals("nested", CacheLock.withLock(locks, target, () -> {
            Files.write(target, new byte[]{1});
            assertEquals(expected, CacheLock.lockFile(locks, target));
            return CacheLock.withLock(locks, target, () -> "nested");
        }));
        try (java.util.stream.Stream<Path> files = Files.list(locks)) {
            assertEquals(1, files.count());
        }
    }

    @Test public void failedOperationReleasesTheLockAndKeepsItsFile() throws Exception {
        Path directory = Files.createTempDirectory("retrospectum-lock-failure");
        Path lock = directory.resolve("locks");
        Path target = directory.resolve("artifact.bin");
        try {
            CacheLock.withLock(lock, target, () -> { throw new IOException("expected failure"); });
            fail("Failure was swallowed");
        } catch (IOException expected) { assertEquals("expected failure", expected.getMessage()); }
        assertEquals("recovered", CacheLock.withLock(lock, target, () -> "recovered"));
        assertTrue(Files.isRegularFile(CacheLock.lockFile(lock, target)));
    }
}
