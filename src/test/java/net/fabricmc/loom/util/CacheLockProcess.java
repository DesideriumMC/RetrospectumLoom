package net.fabricmc.loom.util;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

/** Child process used to test actual OS locking, independently of JVM monitors. */
public final class CacheLockProcess {
    public static void main(String[] args) throws Exception {
        java.nio.file.Path events = Paths.get(args[1]);
        Files.write(events.resolveSibling("ready-" + args[2]), new byte[0]);
        java.nio.file.Path other = events.resolveSibling("ready-" + (args[2].equals("first") ? "second" : "first"));
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
        while (!Files.exists(other)) {
            if (System.nanoTime() > deadline) throw new IllegalStateException("Other lock contender did not start");
            Thread.sleep(10);
        }
        CacheLock.withLock(Paths.get(args[0]), Paths.get(args[3]), () -> {
            Files.write(Paths.get(args[1]), ("begin " + args[2] + "\n").getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            Thread.sleep(150);
            Files.write(Paths.get(args[1]), ("end " + args[2] + "\n").getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.APPEND);
            return null;
        });
    }
}
