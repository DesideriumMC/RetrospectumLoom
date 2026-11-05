package net.fabricmc.loom;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class LocalMinecraftModuleTest {
    @Test public void repairsCorruptAndMissingArtifactsWithoutChangingIdentity() throws Exception {
        Path root = Files.createTempDirectory("ide-module");
        Path binary = Files.write(root.resolve("binary.jar"), new byte[]{1, 2, 3});
        Path sources = Files.write(root.resolve("sources.jar"), new byte[]{4, 5});
        Path repository = root.resolve("repository"), locks = root.resolve("locks");
        String version = LocalMinecraftModule.installFiles(repository, locks, binary, sources);
        Path module = repository.resolve(LocalMinecraftModule.GROUP.replace('.', '/'))
            .resolve(LocalMinecraftModule.ARTIFACT).resolve(version);
        String base = LocalMinecraftModule.ARTIFACT + "-" + version;
        Path jar = module.resolve(base + ".jar"), sourceJar = module.resolve(base + "-sources.jar");
        Path pom = module.resolve(base + ".pom");
        byte[] validPom = Files.readAllBytes(pom);
        Files.write(jar, new byte[]{9});
        Files.delete(sourceJar);
        Files.write(pom, new byte[]{0});
        assertEquals(version, LocalMinecraftModule.installFiles(repository, locks, binary, sources));
        assertArrayEquals(Files.readAllBytes(binary), Files.readAllBytes(jar));
        assertArrayEquals(Files.readAllBytes(sources), Files.readAllBytes(sourceJar));
        assertArrayEquals(validPom, Files.readAllBytes(pom));
        long modified = Files.getLastModifiedTime(jar).toMillis();
        LocalMinecraftModule.installFiles(repository, locks, binary, sources);
        assertEquals(modified, Files.getLastModifiedTime(jar).toMillis());
    }

    @Test public void concurrentInstallersPublishOneCompleteModule() throws Exception {
        Path root = Files.createTempDirectory("ide-module-concurrent");
        Path binary = Files.write(root.resolve("binary.jar"), new byte[65536]);
        Path sources = Files.write(root.resolve("sources.jar"), new byte[]{7});
        ExecutorService executor = Executors.newFixedThreadPool(4);
        try {
            List<Callable<String>> jobs = new ArrayList<>();
            for (int i = 0; i < 12; i++) jobs.add(() -> LocalMinecraftModule.installFiles(
                root.resolve("repository"), root.resolve("locks"), binary, sources));
            Set<String> versions = new HashSet<>();
            for (Future<String> result : executor.invokeAll(jobs)) versions.add(result.get());
            assertEquals(1, versions.size());
            try (java.util.stream.Stream<Path> files = Files.walk(root.resolve("repository"))) {
                List<Path> artifacts = new ArrayList<>();
                files.filter(Files::isRegularFile).forEach(artifacts::add);
                assertEquals(3, artifacts.size());
                assertFalse(artifacts.stream().anyMatch(p -> p.toString().contains(".artifact-")));
            }
        } finally { executor.shutdownNow(); }
    }

    @Test public void attachingSourcesUsesNewIdentity() throws Exception {
        Path root = Files.createTempDirectory("ide-module-sources");
        Path binary = Files.write(root.resolve("binary.jar"), new byte[]{1});
        Path sources = root.resolve("sources.jar");
        String without = LocalMinecraftModule.installFiles(root.resolve("repo"), root.resolve("locks"), binary, sources);
        Files.write(sources, new byte[]{2});
        String with = LocalMinecraftModule.installFiles(root.resolve("repo"), root.resolve("locks"), binary, sources);
        assertNotEquals(without, with);
    }
}
