package net.fabricmc.loom.util;

import net.fabricmc.loom.LoomGradleExtension;
import org.gradle.api.Project;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.temporal.TemporalAmount;
import java.util.zip.GZIPInputStream;

/**
 * Utilities for downloading files from HTTP servers.
 * 
 * (Very largely based off of Loom's old DownloadUtil, but it's more builder-flavored now.
 * Also allows to turn off gzip support because java's gzipinputstream is a bit funky x))
 */
public class DownloadSession {
	public DownloadSession(Project project) {
		this.project = project;
		this.extension = project.getExtensions().getByType(LoomGradleExtension.class);
	}
	
	public DownloadSession(String url, Project project) {
		this(project);
		url(url);
	}
	
	private final Project project;
	private final LoomGradleExtension extension;
	
	private URL url;
	private Path dest;
	//If the server sends an ETag, save it in an auxillary file and send it back to the server when rerequesting the file
	private boolean useEtag = false;
	//Send an Accept-Encoding: gzip header and decompress the file on the client
	private boolean requestGzip = true;
	
	private boolean skipIfExists = false;
	private @Nullable String skipIfSha1 = null;
	private @Nullable TemporalAmount skipIfNewerThan = null;
	
	private boolean quiet;
	
	public DownloadSession url(String url) {
		try {
			this.url = new URL(url);
		} catch (MalformedURLException pls) {
			throw new IllegalArgumentException("Invalid download URL", pls);
		}
		return this;
	}
	
	public DownloadSession dest(Path dest) {
		this.dest = dest;
		return this;
	}
	
	public DownloadSession etag(boolean etag) {
		this.useEtag = etag;
		return this;
	}
	
	public DownloadSession gzip(boolean gzip) {
		this.requestGzip = gzip;
		return this;
	}
	
	public DownloadSession skipIfExists() {
		this.skipIfExists = true;
		return this;
	}
	
	public DownloadSession skipIfSha1Equals(@Nullable String skipIfSha1) {
		this.skipIfSha1 = skipIfSha1;
		return this;
	}
	
	public DownloadSession skipIfNewerThan(@Nullable TemporalAmount skipIfNewerThan) { //e.g. "Period.ofDays()"
		this.skipIfNewerThan = skipIfNewerThan;
		return this;
	}
	
	public DownloadSession quiet() {
		this.quiet = true;
		return this;
	}
	
    public Path download() throws IOException {
        Check.notNull(url, "url");
        Check.notNull(dest, "dest");
        return CacheLock.withLock(net.fabricmc.loom.WellKnownLocations.getCacheLocks(project), dest, this::downloadLocked);
    }

    private Path downloadLocked() throws IOException {
        boolean exists = Files.exists(dest);
        if (exists && !Files.isRegularFile(dest))
            throw new IOException("Download target is not a regular file: " + dest);
        boolean valid = exists && (skipIfSha1 == null || Checksum.compareFileHexHash(dest, skipIfSha1, Checksum.SHA1.get()));
        if (extension.offline) {
            if (!exists) throw new IllegalStateException("Need to download " + url + " to " + dest
                + ", but Gradle was started in offline mode. Aborting.");
            if (!valid) throw new IOException("Offline cache checksum mismatch: source=" + url
                + " path=" + dest + " expectedSha1=" + skipIfSha1);
            info("Not connecting to {} because {} exists and we're in offline mode.", url, dest);
            return dest;
        }
        if (exists && !extension.refreshDependencies) {
            // A declared checksum takes precedence over existence/age shortcuts.
            if (skipIfSha1 != null && valid) return dest;
            if (skipIfSha1 == null && skipIfExists) return dest;
            if (skipIfSha1 == null && skipIfNewerThan != null
                && Files.getLastModifiedTime(dest).toInstant().isAfter(Instant.now().minus(skipIfNewerThan))) return dest;
        }

        Path etagFile = dest.resolveSibling(dest.getFileName() + ".etag");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(20000);
        connection.setReadTimeout(30000);
        try {
            String knownEtag = null;
            // A damaged file must not be accepted through a conditional 304.
            if (useEtag && valid && Files.isRegularFile(etagFile) && !extension.refreshDependencies) {
                knownEtag = new String(Files.readAllBytes(etagFile), StandardCharsets.UTF_8).trim();
                if (knownEtag.isEmpty()) knownEtag = null;
                if (knownEtag != null) {
                    connection.setRequestProperty("If-None-Match", knownEtag);
                    connection.setIfModifiedSince(Files.getLastModifiedTime(dest).toMillis());
                }
            }
            if (requestGzip) connection.setRequestProperty("Accept-Encoding", "gzip");
            lifecycle("Establishing connection to {} (sending etag header: {}, gzip encoding: {})...",
                url, knownEtag != null, requestGzip);
            int code = connection.getResponseCode();
            if (code == HttpURLConnection.HTTP_NOT_MODIFIED) {
                if (knownEtag == null || !valid || !Files.isRegularFile(dest))
                    throw new IOException("Unexpected HTTP 304 without a valid conditional cache: " + url);
                return dest;
            }
            if (code / 100 != 2)
                throw new IOException("Got " + code + " " + connection.getResponseMessage() + " from connection to " + url);

            String receivedEtag = connection.getHeaderField("ETag");
            Path temporary = Files.createTempFile(dest.toAbsolutePath().getParent(), "download-", ".part");
            try {
                try (InputStream network = connection.getInputStream()) {
                    if ("gzip".equalsIgnoreCase(connection.getContentEncoding())) {
                        try (InputStream decoded = new GZIPInputStream(network)) {
                            Files.copy(decoded, temporary, StandardCopyOption.REPLACE_EXISTING);
                        }
                    } else {
                        Files.copy(network, temporary, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
                if (skipIfSha1 != null && !Checksum.compareFileHexHash(temporary, skipIfSha1, Checksum.SHA1.get()))
                    throw new IOException("Download checksum mismatch: source=" + url + " expectedSha1=" + skipIfSha1);
                // Invalidate the old tag before committing a new body. If writing
                // the new metadata fails, the next request will be unconditional.
                Files.deleteIfExists(etagFile);
                AtomicFiles.replace(temporary, dest);
                if (useEtag && receivedEtag != null)
                    AtomicFiles.write(etagFile, receivedEtag.getBytes(StandardCharsets.UTF_8));
            } finally {
                Files.deleteIfExists(temporary);
            }
            return dest;
        } finally {
            // Includes 304, HTTP errors and failures before a temporary file exists.
            connection.disconnect();
        }
    }

	private void info(String x, Object... fmt) {
		if(!quiet) project.getLogger().info(x, fmt);
	}
	
	private void lifecycle(String x, Object... fmt) {
		if(!quiet) project.getLogger().lifecycle(x, fmt);
	}
}
