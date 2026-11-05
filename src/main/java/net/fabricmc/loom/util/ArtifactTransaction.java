package net.fabricmc.loom.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.FileVisitResult;
import java.nio.file.attribute.BasicFileAttributes;

/** Generate and validate beside the destination before committing a file or directory. */
public final class ArtifactTransaction {
    private ArtifactTransaction() {}

    public interface Producer { void produce(Path temporary) throws Exception; }

    public static Path write(Path destination, Producer producer) throws Exception {
        Path target = destination.toAbsolutePath().normalize();
        Files.createDirectories(target.getParent());
        Path staging = Files.createTempDirectory(target.getParent(), ".artifact-");
        Path next = staging.resolve("new").resolve(target.getFileName());
        Path previous = staging.resolve("previous");
        Files.createDirectories(next.getParent());
        boolean keepBackup = false;
        try {
            producer.produce(next);
            if (!Files.exists(next) || (Files.isRegularFile(next) && Files.size(next) == 0))
                throw new IOException("Producer did not create a nonempty artifact: " + target);
            boolean directory = Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS)
                || Files.isDirectory(next, LinkOption.NOFOLLOW_LINKS);
            if (!directory || !Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                AtomicFiles.replace(next, target);
            } else {
                // Replacing a nonempty directory is not an atomic move on all supported filesystems.
                AtomicFiles.replace(target, previous);
                try {
                    AtomicFiles.replace(next, target);
                } catch (Exception commit) {
                    try {
                        deleteTree(target);
                        AtomicFiles.replace(previous, target);
                    } catch (Exception rollback) {
                        keepBackup = true;
                        commit.addSuppressed(rollback);
                        throw new IOException("Artifact commit and rollback failed; previous output retained at " + previous, commit);
                    }
                    throw commit;
                }
            }
            return destination;
        } finally {
            if (!keepBackup) {
                try { deleteTree(staging); }
                catch (IOException cleanup) {
                    org.gradle.api.logging.Logging.getLogger(ArtifactTransaction.class)
                        .warn("Cannot remove artifact staging directory {}", staging, cleanup);
                }
            }
        }
    }

    private static void deleteTree(Path path) throws IOException {
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return;
        Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult postVisitDirectory(Path directory, IOException failure) throws IOException {
                if (failure != null) throw failure;
                Files.delete(directory);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
