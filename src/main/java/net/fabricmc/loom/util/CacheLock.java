package net.fabricmc.loom.util;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;

/** Reentrant JVM and process mutual exclusion in a centralized target-based namespace. */
public final class CacheLock {
    private CacheLock() {}

    public interface Operation<T, E extends Exception> {
        T run() throws E;
    }

    public static Path lockFile(Path directory, Path target) throws IOException {
        Path absolute = target.toAbsolutePath().normalize();
        Files.createDirectories(absolute.getParent());
        // Identify the directory entry, which stays stable when atomic writes replace it.
        Path canonical = absolute.getParent().toRealPath().resolve(absolute.getFileName());
        String key = canonical.toString();
        if (canonical.getFileSystem().getSeparator().equals("\\")) key = key.toLowerCase(Locale.ROOT);
        byte[] hash;
        try {
            hash = java.security.MessageDigest.getInstance("SHA-256")
                .digest(key.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new AssertionError("SHA-256 unavailable", impossible);
        }
        StringBuilder name = new StringBuilder();
        for (byte value : hash) {
            name.append(Character.forDigit((value >>> 4) & 15, 16));
            name.append(Character.forDigit(value & 15, 16));
        }
        return directory.resolve(name + ".lock");
    }

    public static <T, E extends Exception> T withLock(Path directory, Path target, Operation<T, E> operation) throws IOException, E {
        Path absolute = lockFile(directory, target).toAbsolutePath().normalize();
        Files.createDirectories(absolute.getParent());
        Path canonical = absolute.getParent().toRealPath().resolve(absolute.getFileName());
        String key = canonical.toString();
        if (canonical.getFileSystem().getSeparator().equals("\\")) key = key.toLowerCase(Locale.ROOT);
        Object monitor = ("retrospectum-cache-lock:" + key).intern();
        // Providers may invoke the downloader for the same target. The monitor
        // is shared across plugin classloaders; only the outer call takes an OS lock.
        if (Thread.holdsLock(monitor)) return operation.run();
        synchronized (monitor) {
            try (FileChannel channel = FileChannel.open(canonical, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                 FileLock lock = channel.lock()) {
                return operation.run();
            }
        }
    }
}
