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
