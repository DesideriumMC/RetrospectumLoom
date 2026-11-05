package net.fabricmc.loom.task;

import java.nio.file.*;
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
        task.getMethodNames().set(Collections.emptyMap());
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
        }
    }
}
