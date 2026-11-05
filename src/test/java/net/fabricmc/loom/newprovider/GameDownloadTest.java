package net.fabricmc.loom.newprovider;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.util.Checksum;
import net.fabricmc.loom.util.VersionManifest;
import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.Test;
import static org.junit.Assert.*;

public class GameDownloadTest {
    @Test public void cachedGameIsVerifiedRepairedAndFailedRefreshPreservesIt() throws Exception {
        Project project = ProjectBuilder.builder().build();
        LoomGradleExtension extension = project.getExtensions().create("retrospectum", LoomGradleExtension.class, project);
        Path target = project.getProjectDir().toPath().resolve("game.jar");
        byte[] good = new byte[]{1, 2, 3};
        AtomicReference<byte[]> body = new AtomicReference<>(good);
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/game", exchange -> {
            requests.incrementAndGet(); byte[] bytes = body.get();
            exchange.sendResponseHeaders(200, bytes.length);
            try (java.io.OutputStream output = exchange.getResponseBody()) { output.write(bytes); }
        });
        server.start();
        try {
            VersionManifest.Downloads metadata = new VersionManifest.Downloads();
            metadata.url = "http://127.0.0.1:" + server.getAddress().getPort() + "/game";
            metadata.sha1 = Checksum.bytesHexHash(good, Checksum.SHA1.get());
            VanillaJarFetcher fetcher = new VanillaJarFetcher(project, extension);
            fetcher.downloadGameJar(metadata, target);
            fetcher.downloadGameJar(metadata, target);
            assertEquals(1, requests.get());
            Files.write(target, new byte[]{9});
            fetcher.downloadGameJar(metadata, target);
            assertEquals(2, requests.get());
            assertArrayEquals(good, Files.readAllBytes(target));
            extension.refreshDependencies = true; body.set(new byte[]{8});
            try { fetcher.downloadGameJar(metadata, target); fail("Accepted incorrect game hash"); }
            catch (java.io.IOException expected) { assertTrue(expected.getMessage().contains("checksum mismatch")); }
            assertArrayEquals(good, Files.readAllBytes(target));
            metadata.sha1 = null;
            try { fetcher.downloadGameJar(metadata, target); fail("Official game without hash accepted"); }
            catch (IllegalStateException expected) { assertTrue(expected.getMessage().contains("SHA-1")); }
            extension.refreshDependencies = false;
            fetcher.customManifestUrl("https://example.invalid/custom.json");
            assertEquals(target, fetcher.downloadGameJar(metadata, target));
            metadata.sha1 = "invalid";
            try { fetcher.downloadGameJar(metadata, target); fail("Malformed hash accepted"); }
            catch (IllegalStateException expected) { assertTrue(expected.getMessage().contains("SHA-1")); }
        } finally { server.stop(0); }
    }
}
