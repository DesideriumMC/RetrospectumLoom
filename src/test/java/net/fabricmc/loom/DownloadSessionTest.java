package net.fabricmc.loom;

import com.sun.net.httpserver.HttpServer;
import net.fabricmc.loom.util.DownloadSession;
import net.fabricmc.loom.util.Checksum;
import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.Test;
import java.nio.file.*;
import java.net.InetSocketAddress;
import java.io.IOException;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

public class DownloadSessionTest {
    private static final class ProviderProbe extends net.fabricmc.loom.newprovider.NewProvider<ProviderProbe> {
        ProviderProbe(Project project, LoomGradleExtension extension) { super(project, extension); }
        Path fetch(Path target, String url) throws Exception {
            return getOrCreate(target, path -> newDownloadSession(url).dest(path).download());
        }
    }

    @Test(timeout = 20000) public void providerAndDownloaderShareOneReentrantCentralLock() throws Exception {
        Project project = ProjectBuilder.builder().build();
        LoomGradleExtension extension = project.getExtensions().create("minecraft", LoomGradleExtension.class, project);
        Path target = project.getProjectDir().toPath().resolve("fresh-cache/download.bin");
        byte[] bytes = "provider download".getBytes("UTF-8");
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/file", exchange -> {
            requests.incrementAndGet();
            exchange.sendResponseHeaders(200, bytes.length);
            try (java.io.OutputStream out = exchange.getResponseBody()) { out.write(bytes); }
        });
        server.start();
        try {
            ProviderProbe provider = new ProviderProbe(project, extension);
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/file";
            assertEquals(target, provider.fetch(target, url));
            assertEquals(target, provider.fetch(target, url));
            assertEquals(1, requests.get());
            assertArrayEquals(bytes, Files.readAllBytes(target));
            Path lock = net.fabricmc.loom.util.CacheLock.lockFile(WellKnownLocations.getCacheLocks(project), target);
            assertTrue(Files.isRegularFile(lock));
            assertTrue(lock.getFileName().toString().matches("[0-9a-f]{64}\\.lock"));
            assertFalse(Files.exists(target.resolveSibling("download.bin.lock")));
            assertFalse(Files.exists(target.resolveSibling("download.bin.provider.lock")));
        } finally { server.stop(0); }
    }

    @Test public void concurrentDownloadOfflineAndFailedRefreshPreserveCache() throws Exception {
        Project project = ProjectBuilder.builder().build();
        LoomGradleExtension extension = project.getExtensions().create("minecraft", LoomGradleExtension.class, project);
        Path dest = project.getProjectDir().toPath().resolve("cache with spaces/file.bin");
        byte[] good = "accepted artifact".getBytes("UTF-8");
        String digest = Checksum.bytesHexHash(good, Checksum.SHA1.get());
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/file", exchange -> {
            requests.incrementAndGet();
            byte[] body = extension.refreshDependencies ? "corrupt".getBytes("UTF-8") : good;
            exchange.sendResponseHeaders(200, body.length);
            try (java.io.OutputStream out = exchange.getResponseBody()) { out.write(body); }
        });
        server.start();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/file";
        try {
            Callable<Path> task = () -> new DownloadSession(url, project).dest(dest).skipIfSha1Equals(digest).download();
            Future<Path> a = pool.submit(task), b = pool.submit(task);
            a.get(15, TimeUnit.SECONDS); b.get(15, TimeUnit.SECONDS);
            assertEquals(1, requests.get());
            extension.offline = true;
            task.call();
            Files.write(dest, "damaged".getBytes("UTF-8"));
            try { task.call(); fail("offline corruption accepted"); }
            catch (IOException expected) { assertTrue(expected.getMessage().contains("checksum mismatch")); }
            Files.write(dest, good);
            extension.offline = false; extension.refreshDependencies = true;
            try { task.call(); fail("corrupt refresh accepted"); }
            catch (IOException expected) { assertTrue(expected.getMessage().contains("checksum mismatch")); }
            assertArrayEquals(good, Files.readAllBytes(dest));
            try (java.util.stream.Stream<Path> paths = Files.list(dest.getParent())) {
                assertFalse(paths.anyMatch(p -> p.toString().endsWith(".part")));
            }
        } finally { pool.shutdownNow(); server.stop(0); }
    }
}
