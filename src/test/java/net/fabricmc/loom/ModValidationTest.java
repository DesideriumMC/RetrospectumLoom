package net.fabricmc.loom;

import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import org.gradle.api.GradleException;
import java.io.File;
import java.util.jar.*;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.*;

public class ModValidationTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();
    private File archive(boolean refmap) throws Exception {
        File file = tmp.newFile();
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().putValue("Manifest-Version", "1.0");
        manifest.getMainAttributes().putValue("RetroMixinConfigs", "mixins.example.json");
        try (JarOutputStream out = new JarOutputStream(new java.io.FileOutputStream(file), manifest)) {
            out.putNextEntry(new JarEntry("mixins.example.json"));
            out.write("{\"refmap\":\"example.refmap.json\"}".getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
            if (refmap) { out.putNextEntry(new JarEntry("example.refmap.json")); out.write("{}".getBytes(StandardCharsets.UTF_8)); out.closeEntry(); }
        }
        return file;
    }
    @Test public void validatesPackagedRefmap() throws Exception { ModValidation.archive(archive(true)); }
    @Test public void rejectsExcludedRefmap() throws Exception {
        try { ModValidation.archive(archive(false)); fail("missing refmap accepted"); }
        catch (GradleException e) { assertTrue(e.getMessage().contains("Missing Mixin refmap")); }
    }
    @Test public void rejectsMissingRuntime() {
        try { ModValidation.runtime(java.util.Collections.emptyList()); fail("missing runtime accepted"); }
        catch (GradleException e) { assertTrue(e.getMessage().contains("found 0")); }
    }
    @Test public void rejectsJava9BytecodeBeforeLaunch() throws Exception {
        File file=tmp.newFile();
        try (JarOutputStream out=new JarOutputStream(new java.io.FileOutputStream(file))) {
            out.putNextEntry(new JarEntry("Unsupported.class"));
            out.write(new byte[]{(byte)0xca,(byte)0xfe,(byte)0xba,(byte)0xbe,0,0,0,53});
            out.closeEntry();
        }
        try { ModValidation.archive(file); fail("Java 9 class accepted"); }
        catch (GradleException e) { assertTrue(e.getMessage().contains("major=53")); }
    }

    @Test public void rejectsJava8BytecodeOnOriginalForge() throws Exception {
        File file=tmp.newFile();
        try (JarOutputStream out=new JarOutputStream(new java.io.FileOutputStream(file))) {
            out.putNextEntry(new JarEntry("Java8.class"));
            out.write(new byte[]{(byte)0xca,(byte)0xfe,(byte)0xba,(byte)0xbe,0,0,0,52});
            out.closeEntry();
        }
        try { ModValidation.archive(file); fail("Java 8 class accepted"); }
        catch (GradleException e) { assertTrue(e.getMessage().contains("major=52")); }
    }
    @Test public void rejectsDuplicateRuntimes() throws Exception {
        java.util.List<File> files = new java.util.ArrayList<>();
        for (int i=0; i<2; i++) {
            File file=tmp.newFile("runtime"+i+".jar"); files.add(file);
            try (JarOutputStream out=new JarOutputStream(new java.io.FileOutputStream(file))) {
                out.putNextEntry(new JarEntry("org/spongepowered/asm/launch/MixinBootstrap.class"));
                out.closeEntry();
            }
        }
        try { ModValidation.runtime(files); fail("duplicate runtimes accepted"); }
        catch (GradleException e) { assertTrue(e.getMessage().contains("found 2")); }
    }
}
