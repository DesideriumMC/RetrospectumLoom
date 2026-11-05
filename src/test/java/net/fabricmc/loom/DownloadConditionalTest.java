package net.fabricmc.loom;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.loom.util.Checksum;
import net.fabricmc.loom.util.DownloadSession;
import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.Test;
import static org.junit.Assert.*;

public class DownloadConditionalTest {
    @Test public void gzipDownloadAndBrokenRefreshPreserveBodyAndEtag() throws Exception {
        Project project = ProjectBuilder.builder().build();
        LoomGradleExtension extension = project.getExtensions().create("minecraft", LoomGradleExtension.class, project);
        Path dest = project.getProjectDir().toPath().resolve("download.bin");
        byte[] good = "uncompressed artifact".getBytes(StandardCharsets.UTF_8);
        java.io.ByteArrayOutputStream compressed = new java.io.ByteArrayOutputStream();
        try (java.util.zip.GZIPOutputStream gzip = new java.util.zip.GZIPOutputStream(compressed)) { gzip.write(good); }
        AtomicReference<byte[]> response = new AtomicReference<>(compressed.toByteArray());
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/file", exchange -> {
            byte[] bytes = response.get();
            exchange.getResponseHeaders().set("Content-Encoding", "GZIP");
            exchange.getResponseHeaders().set("ETag", "\"compressed-v1\"");
            exchange.sendResponseHeaders(200, bytes.length);
            try (java.io.OutputStream out = exchange.getResponseBody()) { out.write(bytes); }
        });
        server.start();
        String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/file";
        String digest = Checksum.bytesHexHash(good, Checksum.SHA1.get());
        try {
            new DownloadSession(url, project).dest(dest).etag(true).skipIfSha1Equals(digest).download();
            assertArrayEquals(good, Files.readAllBytes(dest));
            Path tag = dest.resolveSibling("download.bin.etag");
            byte[] previousTag = Files.readAllBytes(tag);
            extension.refreshDependencies = true;
            response.set("invalid gzip header".getBytes(StandardCharsets.UTF_8));
            try { new DownloadSession(url, project).dest(dest).etag(true).skipIfSha1Equals(digest).download(); fail("Accepted broken gzip"); }
            catch (IOException expected) { /* Failed before replacing a valid cached body. */ }
            assertArrayEquals(good, Files.readAllBytes(dest));
            assertArrayEquals(previousTag, Files.readAllBytes(tag));
            try (java.util.stream.Stream<Path> paths = Files.list(dest.getParent())) {
                assertFalse(paths.anyMatch(path -> path.toString().endsWith(".part")));
            }
        } finally { server.stop(0); }
    }

    @Test public void conditionalCacheNeverOverridesChecksumAndStaleTagsAreRemoved() throws Exception {
        Project project = ProjectBuilder.builder().build();
        project.getExtensions().create("minecraft", LoomGradleExtension.class, project);
        Path dest = project.getProjectDir().toPath().resolve("downloads/file.bin");
        Path tagFile = dest.resolveSibling("file.bin.etag");
        byte[] good = "original artifact".getBytes(StandardCharsets.UTF_8);
        AtomicReference<byte[]> body = new AtomicReference<>(good);
        AtomicReference<String> etag = new AtomicReference<>("\"v1\"");
        AtomicReference<String> received = new AtomicReference<>();
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/file", exchange -> {
            requests.incrementAndGet();
            String conditional = exchange.getRequestHeaders().getFirst("If-None-Match");
            received.set(conditional);
            if (etag.get() != null && etag.get().equals(conditional)) {
                exchange.sendResponseHeaders(304, -1);
                exchange.close();
            } else {
                if (etag.get() != null) exchange.getResponseHeaders().set("ETag", etag.get());
                byte[] response = body.get();
                exchange.sendResponseHeaders(200, response.length);
                try (java.io.OutputStream out = exchange.getResponseBody()) { out.write(response); }
            }
        });
        server.start();
        String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/file";
        try {
            new DownloadSession(url, project).dest(dest).etag(true).download();
            assertEquals("\"v1\"", new String(Files.readAllBytes(tagFile), StandardCharsets.UTF_8));
            new DownloadSession(url, project).dest(dest).etag(true).download();
            assertEquals("\"v1\"", received.get());
            assertArrayEquals(good, Files.readAllBytes(dest));
            Files.write(dest, "damaged".getBytes(StandardCharsets.UTF_8));
            new DownloadSession(url, project).dest(dest).etag(true).skipIfExists()
                .skipIfNewerThan(Duration.ofDays(1))
                .skipIfSha1Equals(Checksum.bytesHexHash(good, Checksum.SHA1.get())).download();
            assertNull(received.get());
            assertArrayEquals(good, Files.readAllBytes(dest));
            etag.set(null);
            byte[] next = "new body without a tag".getBytes(StandardCharsets.UTF_8);
            body.set(next);
            new DownloadSession(url, project).dest(dest).etag(true).download();
            assertFalse(Files.exists(tagFile));
            assertArrayEquals(next, Files.readAllBytes(dest));
            new DownloadSession(url, project).dest(dest).etag(true).download();
            assertNull(received.get());
            assertEquals(5, requests.get());
        } finally { server.stop(0); }
    }

    @Test public void unconditional304AndHttpFailurePreserveExistingArtifact() throws Exception {
        Project project = ProjectBuilder.builder().build();
        project.getExtensions().create("minecraft", LoomGradleExtension.class, project);
        Path dest = project.getProjectDir().toPath().resolve("file.bin");
        byte[] good = "previous".getBytes(StandardCharsets.UTF_8);
        Files.write(dest, good);
        AtomicInteger status = new AtomicInteger(304);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/file", exchange -> {
            exchange.sendResponseHeaders(status.get(), -1);
            exchange.close();
        });
        server.start();
        String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/file";
        try {
            for (int code : new int[]{304, 503}) {
                status.set(code);
                try { new DownloadSession(url, project).dest(dest).download(); fail("Accepted HTTP " + code); }
                catch (IOException expected) { assertTrue(expected.getMessage().contains(Integer.toString(code))); }
                assertArrayEquals(good, Files.readAllBytes(dest));
            }
            try (java.util.stream.Stream<Path> paths = Files.list(dest.getParent())) {
                assertFalse(paths.anyMatch(path -> path.toString().endsWith(".part")));
            }
        } finally { server.stop(0); }
    }
}
