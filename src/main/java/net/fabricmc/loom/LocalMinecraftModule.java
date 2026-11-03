package net.fabricmc.loom;

import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
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
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        update(digest, binary);
        boolean hasSources = Files.isRegularFile(sources);
        if (hasSources) update(digest, sources);
        StringBuilder version = new StringBuilder();
        for (byte b : digest.digest()) version.append(String.format("%02x", b & 255));
        Path repository = WellKnownLocations.getProjectCache(project).resolve("ide-maven");
        Path directory = repository.resolve(GROUP.replace('.', '/')).resolve(ARTIFACT).resolve(version.toString());
        Files.createDirectories(directory);
        String base = ARTIFACT + "-" + version;
        copy(binary, directory.resolve(base + ".jar"));
        if (hasSources) copy(sources, directory.resolve(base + "-sources.jar"));
        String pom = "<project xmlns=\"http://maven.apache.org/POM/4.0.0\"><modelVersion>4.0.0</modelVersion><groupId>" + GROUP + "</groupId><artifactId>" + ARTIFACT + "</artifactId><version>" + version + "</version></project>";
        Path pomFile = directory.resolve(base + ".pom");
        if (!Files.exists(pomFile)) Files.write(pomFile, pom.getBytes(StandardCharsets.UTF_8));
        project.getDependencies().add(Constants.MINECRAFT_NAMED, GROUP + ":" + ARTIFACT + ":" + version);
    }

    private static void update(MessageDigest digest, Path path) throws Exception {
        try (java.io.InputStream input = Files.newInputStream(path)) {
            byte[] buffer = new byte[8192]; int size;
            while ((size = input.read(buffer)) != -1) digest.update(buffer, 0, size);
        }
    }
    private static void copy(Path source, Path target) throws Exception {
        if (!Files.exists(target)) Files.copy(source, target);
    }
}
