package net.fabricmc.loom;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.Test;
import static org.junit.Assert.*;

public class ReleaseArchiveTest {
    @Test public void manifestIsReadableEvenWithEarlierSortingEntries() throws Exception {
        Path jar = Files.createTempFile("retrospectum-archive", ".jar");
        try {
            try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(jar))) {
                out.putNextEntry(new ZipEntry("META-INF/AAA.txt"));
                out.write(1);
                out.closeEntry();
                out.putNextEntry(new ZipEntry("META-INF/MANIFEST.MF"));
                out.write("Manifest-Version: 1.0\r\nMixinConfigs: example.json\r\n\r\n".getBytes("UTF-8"));
                out.closeEntry();
            }
            ReleaseArchive.normalize(jar);
            try (JarInputStream in = new JarInputStream(Files.newInputStream(jar))) {
                assertNotNull(in.getManifest());
                assertEquals("example.json", in.getManifest().getMainAttributes().getValue("MixinConfigs"));
            }
            byte[] first = Files.readAllBytes(jar);
            ReleaseArchive.normalize(jar);
            assertArrayEquals(first, Files.readAllBytes(jar));
        } finally {
            Files.deleteIfExists(jar);
        }
    }
}
