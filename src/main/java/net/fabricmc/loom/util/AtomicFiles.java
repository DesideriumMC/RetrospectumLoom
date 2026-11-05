package net.fabricmc.loom.util;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Same-filesystem replacement, with a fallback for filesystems lacking atomic move. */
public final class AtomicFiles {
    private AtomicFiles() {}

    public static void replace(Path temporary, Path destination) throws IOException {
        try {
            Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException unsupported) {
            Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static void write(Path destination, byte[] bytes) throws IOException {
        Path directory = destination.toAbsolutePath().getParent();
        Files.createDirectories(directory);
        Path temporary = Files.createTempFile(directory, "metadata-", ".tmp");
        try {
            Files.write(temporary, bytes);
            replace(temporary, destination);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
