package net.fabricmc.loom.yoinked.stitch;

import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;

import static org.junit.Assert.*;

public class ClassMergerCoolerTest {
    private static final String SIDE_ONLY = "Lcpw/mods/fml/relauncher/SideOnly;";

    @Test
    public void sidedMembersAreVisibleToLegacyFmlSideTransformer() {
        byte[] merged = new ClassMergerCooler()
                .sideEnum("Lcpw/mods/fml/relauncher/Side;")
                .sideDescriptorAnnotation(SIDE_ONLY)
                .merge(fixture(true), fixture(false));
        ClassNode node = new ClassNode();
        new ClassReader(merged).accept(node, 0);

        // Legacy FML SideTransformer reads visibleAnnotations, not invisibleAnnotations.
        // Invisible annotations leave client methods in the server class, whose
        // descriptors/exception tables can force the JVM to load client-only types.
        assertEquals(SIDE_ONLY, node.methods.get(0).visibleAnnotations.get(0).desc);
        assertEquals("CLIENT", ((String[]) node.methods.get(0).visibleAnnotations.get(0).values.get(1))[1]);
        assertEquals(SIDE_ONLY, node.fields.get(0).visibleAnnotations.get(0).desc);
        assertNull(node.methods.get(0).invisibleAnnotations);
        assertNull(node.fields.get(0).invisibleAnnotations);
    }

    private static byte[] fixture(boolean client) {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V1_6, Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT,
                "fixture/Common", null, "java/lang/Object", null);
        if (client) {
            writer.visitField(Opcodes.ACC_PUBLIC, "clientField", "Lfixture/ClientOnly;", null, null).visitEnd();
            writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT,
                    "clientMethod", "()Lfixture/ClientOnly;", null, null).visitEnd();
        }
        writer.visitEnd();
        return writer.toByteArray();
    }
}
