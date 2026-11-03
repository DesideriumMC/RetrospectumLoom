package net.fabricmc.loom;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.jar.JarFile;
import org.gradle.api.GradleException;

/** Checks the actual archive, so excluded resources and stale AP outputs cannot pass. */
final class ModValidation {
    static void archive(File file) {
        try (JarFile jar = new JarFile(file)) {
            java.util.Enumeration<java.util.jar.JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                java.util.jar.JarEntry entry = entries.nextElement();
                if (!entry.getName().endsWith(".class")) continue;
                try (java.io.DataInputStream in = new java.io.DataInputStream(jar.getInputStream(entry))) {
                    if (in.readInt() != 0xCAFEBABE) throw new GradleException("Invalid class header: " + file + "!" + entry.getName());
                    int minor = in.readUnsignedShort(), major = in.readUnsignedShort();
                    if (major > 50 || minor == 65535)
                        throw new GradleException("RetroMixin official Forge baseline targets Java 6 mod bytecode: unsupported class major=" + major + " in " + file + "!" + entry.getName() + "; compile with a JDK 8 compiler, sourceCompatibility/targetCompatibility 1.6.");
                }
            }
            if (jar.getManifest() == null) return;
            String configs = jar.getManifest().getMainAttributes().getValue("RetroMixinConfigs");
            if (configs == null) return;
            for (String name : configs.split(",")) {
                name = name.trim();
                if (jar.getJarEntry(name) == null) throw new GradleException("Missing Mixin config '" + name + "' in " + file);
                try (InputStreamReader reader = new InputStreamReader(jar.getInputStream(jar.getJarEntry(name)), StandardCharsets.UTF_8)) {
                    JsonObject config = new JsonParser().parse(reader).getAsJsonObject();
                    if (!config.has("refmap") || jar.getJarEntry(config.get("refmap").getAsString()) == null)
                        throw new GradleException("Missing Mixin refmap for '" + name + "' in " + file + "; configure retrospectum.mixin(SourceSet, refmap) and enable its annotationProcessor.");
                }
            }
        } catch (java.io.IOException e) { throw new GradleException("Cannot validate mod archive " + file, e); }
    }

    static void runtime(Iterable<File> classpath) {
        int count = 0, bootstraps = 0;
        for (File file : classpath) {
            if (!file.isFile() || !file.getName().endsWith(".jar")) continue;
            try (JarFile jar = new JarFile(file)) {
                if (jar.getJarEntry("org/spongepowered/asm/launch/MixinBootstrap.class") != null) count++;
                if (jar.getJarEntry("dev/desiderium/retro/mixin/bootstrap/RetroMixinTweaker.class") != null) bootstraps++;
            } catch (java.io.IOException e) { throw new GradleException("Cannot inspect runtime dependency " + file, e); }
        }
        if (count != 1 || bootstraps != 1) throw new GradleException("Expected exactly one shared Mixin runtime dependency, found " + count + " (RetroMixin Bootstraps=" + bootstraps + "); add the RetroMixin Bootstrap to the run classpath. Do not bundle Mixin into your mod.");
    }
}
