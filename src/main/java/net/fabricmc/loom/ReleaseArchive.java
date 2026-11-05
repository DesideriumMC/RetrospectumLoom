package net.fabricmc.loom;

import java.nio.file.Path;
import java.nio.file.Files;
import net.fabricmc.loom.util.AtomicFiles;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import java.util.zip.ZipEntry;
import java.util.Enumeration;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Calendar;
import java.io.IOException;
import java.io.InputStream;

import org.gradle.api.GradleException;

/** Canonical ZIP order and timestamps for remapper output. */
public final class ReleaseArchive {
    public static void normalize(Path path) {
        Path temp = null;
        try {
            temp = Files.createTempFile(path.getParent(), "release-", ".tmp");
            try (ZipFile input = new ZipFile(path.toFile());
                 ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(temp))) {
                List<String> names = new ArrayList<>();
                Enumeration<? extends ZipEntry> entries = input.entries();
                while (entries.hasMoreElements()) names.add(entries.nextElement().getName());
                Collections.sort(names);
                if (names.remove("META-INF/MANIFEST.MF")) names.add(0, "META-INF/MANIFEST.MF");
                Calendar timestamp = Calendar.getInstance();
                timestamp.clear(); timestamp.set(2000, 0, 1, 0, 0, 0);
                for (String name : names) {
                    ZipEntry entry = new ZipEntry(name);
                    entry.setTime(timestamp.getTimeInMillis());
                    output.putNextEntry(entry);
                    try (InputStream in = input.getInputStream(input.getEntry(name))) {
                        byte[] bytes = new byte[65536]; int count;
                        while ((count = in.read(bytes)) != -1) output.write(bytes, 0, count);
                    }
                    output.closeEntry();
                }
            }
            AtomicFiles.replace(temp, path);
        } catch (IOException e) {
            throw new GradleException("Cannot normalize release archive " + path, e);
        } finally {
            if (temp != null) try { Files.deleteIfExists(temp); } catch (IOException ignored) {}
        }
    }
}
