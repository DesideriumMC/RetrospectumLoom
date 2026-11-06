package net.fabricmc.loom.task;

import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loom.util.ZipUtil;
import java.util.*;
import java.util.zip.*;
import net.fabricmc.loom.Constants;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.Test;
import org.objectweb.asm.*;
import static org.junit.Assert.*;

public class RemapModDependenciesTaskTest {
    @Test public void remapsChangedInputAndKeepsPreviousDirectoryOnFailure() throws Exception {
        org.gradle.api.Project project = ProjectBuilder.builder().build();
        RemapModDependenciesTask task = project.getTasks().create("remapMods", RemapModDependenciesTask.class);
        Path root = project.getProjectDir().toPath(), input = root.resolve("mod.jar"), output = root.resolve("mapped");
        task.getArchives().from(input);
        task.getOutputDirectory().set(output.toFile());
        task.getSrgLines().set(Collections.emptyList());
        task.getFieldNames().put("field_1", "readable");
        task.getMethodNames().put("func_110302_j", "setupCustomSkin");
        task.getDistributionNamespace().set(Constants.INTERMEDIATE_NAMING_SCHEME);
        task.getAsm4Compatibility().set(false);
        write(input, 1);
        task.remap();
        Path mapped = output.resolve(RemapModDependenciesTask.outputName(input.toFile()));
        byte[] previous = Files.readAllBytes(mapped);
        try (ZipFile jar = new ZipFile(mapped.toFile())) {
            ClassReader reader = new ClassReader(jar.getInputStream(jar.getEntry("Probe.class")));
            final List<String> fields = new ArrayList<>();
            reader.accept(new ClassVisitor(Opcodes.ASM4) {
                @Override public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
                    fields.add(name); return null;
                }
            }, 0);
            assertEquals(Collections.singletonList("readable"), fields);
        }
        try (FileSystem jar = ZipUtil.openFs(mapped)) {
            try (java.io.Reader reader = Files.newBufferedReader(jar.getPath("/custom.json"))) {
                JsonObject refmap = new JsonParser().parse(reader).getAsJsonObject();
                JsonObject mappings = refmap.getAsJsonObject("mappings");
                JsonObject context = refmap.getAsJsonObject("data").getAsJsonObject("searge");
                assertEquals(mappings, context);
                assertEquals("Lexample/Player;setupCustomSkin()V",
                    mappings.getAsJsonObject("example/Probe").get("func_110302_j").getAsString());
                assertEquals("Lexample/Player;readable:I",
                    mappings.getAsJsonObject("example/Probe").get("field").getAsString());
            }
            assertEquals("{\"refmap\":\"custom.json\"}",
                new String(Files.readAllBytes(jar.getPath("/explicit.mixins.json")), StandardCharsets.UTF_8));
        }
        Files.write(input, new byte[]{1});
        try { task.remap(); fail("Accepted corrupt archive"); } catch (Exception expected) { }
        assertArrayEquals(previous, Files.readAllBytes(mapped));
        write(input, 2);
        task.remap();
        assertFalse(Arrays.equals(previous, Files.readAllBytes(mapped)));
        try (java.util.stream.Stream<Path> files = Files.list(root)) {
            assertFalse(files.anyMatch(p -> p.getFileName().toString().startsWith(".artifact-")));
        }
    }

    @Test public void equalBasenamesHaveDistinctOutputPaths() {
        assertNotEquals(RemapModDependenciesTask.outputName(new java.io.File("first/mod.jar")),
            RemapModDependenciesTask.outputName(new java.io.File("second/mod.jar")));
    }

    private static void write(Path output, int value) throws Exception {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V1_6, Opcodes.ACC_PUBLIC, "Probe", null, "java/lang/Object", null);
        writer.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL, "field_1", "I", null, value).visitEnd();
        writer.visitEnd();
        try (ZipOutputStream jar = new ZipOutputStream(Files.newOutputStream(output))) {
            jar.putNextEntry(new ZipEntry("Probe.class")); jar.write(writer.toByteArray()); jar.closeEntry();
            writeResource(jar, "explicit.mixins.json", "{\"refmap\":\"custom.json\"}");
            String mappings = "{\"example/Probe\":{\"func_110302_j\":\"Lexample/Player;func_110302_j()V\","
                + "\"field\":\"Lexample/Player;field_1:I\"}}";
            writeResource(jar, "custom.json", "{\"mappings\":" + mappings + ",\"data\":{\"searge\":" + mappings + "}}");
        }
    }

    private static void writeResource(ZipOutputStream jar, String name, String content) throws Exception {
        jar.putNextEntry(new ZipEntry(name));
        jar.write(content.getBytes(StandardCharsets.UTF_8));
        jar.closeEntry();
    }
}
