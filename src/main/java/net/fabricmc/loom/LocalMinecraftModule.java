package net.fabricmc.loom;

import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import net.fabricmc.loom.util.ArtifactTransaction;
import net.fabricmc.loom.util.CacheLock;
import org.gradle.api.Project;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;

/** Local-only Maven identity lets IDE source queries associate the generated source artifact. */
final class LocalMinecraftModule {
    static final String GROUP = "dev.desiderium.retro.internal.minecraft";
    static final String ARTIFACT = "minecraft-forge-named";

    static void registerRepository(Project project) {
        Path repository = WellKnownLocations.getProjectCache(project).resolve("ide-maven");
        MavenArtifactRepository repo = project.getRepositories().maven(r -> {
            r.setName("RetroMixinLocalMinecraftSources"); r.setUrl(repository.toUri());
            r.content(content -> content.includeGroup(GROUP));
        });
        project.getRepositories().exclusiveContent(content -> {
            content.forRepository(() -> repo);
            content.filter(filter -> filter.includeGroup(GROUP));
        });
    }

    static void install(Project project, Path binary, Path sources) throws Exception {
        String version = installFiles(WellKnownLocations.getProjectCache(project).resolve("ide-maven"),
            WellKnownLocations.getCacheLocks(project), binary, sources);
        project.getDependencies().add(Constants.MINECRAFT_NAMED, GROUP + ":" + ARTIFACT + ":" + version);
    }

    static String installFiles(Path repository, Path locks, Path binary, Path sources) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] binaryHash = hash(binary);
        update(digest, binary);
        boolean hasSources = Files.isRegularFile(sources);
        byte[] sourcesHash = hasSources ? hash(sources) : null;
        if (hasSources) update(digest, sources);
        StringBuilder version = new StringBuilder();
        for (byte b : digest.digest()) version.append(String.format("%02x", b & 255));
        Path directory = repository.resolve(GROUP.replace('.', '/')).resolve(ARTIFACT).resolve(version.toString());
        String base = ARTIFACT + "-" + version;
        String pom = "<project xmlns=\"http://maven.apache.org/POM/4.0.0\"><modelVersion>4.0.0</modelVersion><groupId>" + GROUP + "</groupId><artifactId>" + ARTIFACT + "</artifactId><version>" + version + "</version></project>";
        CacheLock.withLock(locks, directory, () -> {
            Files.createDirectories(directory);
            copy(binary, directory.resolve(base + ".jar"), binaryHash);
            if (hasSources) copy(sources, directory.resolve(base + "-sources.jar"), sourcesHash);
            Path pomFile = directory.resolve(base + ".pom");
            byte[] bytes = pom.getBytes(StandardCharsets.UTF_8);
            if (!Files.isRegularFile(pomFile) || !Arrays.equals(bytes, Files.readAllBytes(pomFile)))
                ArtifactTransaction.write(pomFile, temporary -> Files.write(temporary, bytes));
            return null;
        });
        return version.toString();
    }

    private static void update(MessageDigest digest, Path path) throws Exception {
        try (java.io.InputStream input = Files.newInputStream(path)) {
            byte[] buffer = new byte[8192]; int size;
            while ((size = input.read(buffer)) != -1) digest.update(buffer, 0, size);
        }
    }
    private static void copy(Path source, Path target, byte[] expected) throws Exception {
        if (!Arrays.equals(expected, hash(source)))
            throw new java.io.IOException("Source changed while installing IDE artifact: " + source);
        if (Files.isRegularFile(target) && Arrays.equals(expected, hash(target))) return;
        ArtifactTransaction.write(target, temporary -> {
            Files.copy(source, temporary);
            if (!Arrays.equals(expected, hash(temporary)))
                throw new java.io.IOException("Source changed while installing IDE artifact: " + source);
        });
    }

    private static byte[] hash(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        update(digest, path);
        return digest.digest();
    }
}
