package net.fabricmc.loom.task;

import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;

/** Restore obfuscator-stripped enum switch helper ownership for decompilation only. */
final class DecompilerInput {
    private static final Map<String, String> HELPERS = new LinkedHashMap<>();
    static {
        HELPERS.put("net/minecraft/entity/EnumEntitySizeHelper", "net/minecraft/entity/EnumEntitySize");
        HELPERS.put("net/minecraft/world/gen/structure/EnumDoorHelper", "net/minecraft/world/gen/structure/ComponentStronghold");
        HELPERS.put("net/minecraft/client/settings/EnumOptionsHelper", "net/minecraft/client/settings/GameSettings");
    }
    static Path prepare(Path input, Path output) throws Exception {
        try (JarFile jar = new JarFile(input.toFile()); JarOutputStream out = new JarOutputStream(Files.newOutputStream(output))) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                byte[] bytes;
                try (java.io.InputStream stream = jar.getInputStream(entry)) { bytes = read(stream); }
                if (entry.getName().endsWith(".class")) {
                    ClassReader reader = new ClassReader(bytes);
                    String name = reader.getClassName();
                    if (HELPERS.containsKey(name) || HELPERS.containsValue(name)) {
                        ClassWriter writer = new ClassWriter(0);
                        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
                            @Override public void visitInnerClass(String inner, String outer, String simple, int access) {
                                if (!HELPERS.containsKey(inner)) super.visitInnerClass(inner, outer, simple, access);
                            }
                            @Override public void visitEnd() {
                                HELPERS.forEach((helper, owner) -> {
                                    if (name.equals(helper) || name.equals(owner)) super.visitInnerClass(helper, owner, null, Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC);
                                });
                                super.visitEnd();
                            }
                        }, 0);
                        bytes = writer.toByteArray();
                    }
                }
                out.putNextEntry(new JarEntry(entry.getName())); out.write(bytes); out.closeEntry();
            }
        }
        return output;
    }
    private static byte[] read(java.io.InputStream in) throws java.io.IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[8192]; int count;
        while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
        return out.toByteArray();
    }
}
