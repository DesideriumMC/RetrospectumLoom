package net.fabricmc.loom.mcp;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;
import static org.junit.Assert.*;

public class MixinMappingsTest {
    @Test public void inventedInnerClassNameCanBeUsedInJavaSource() {
        Srg mappings = new Srg();
        mappings.putClassMapping("a", "example/Outer");
        JarScanData scan = new JarScanData();
        scan.innerClassData.put("a", java.util.Collections.singleton("b"));
        mappings.augment(scan);
        String name = mappings.classMappings.get("b").substring("example/".length());
        assertTrue(Character.isJavaIdentifierStart(name.charAt(0)));
        for (char c : name.toCharArray()) assertTrue(Character.isJavaIdentifierPart(c));
    }

    @Test public void inversePreservesOwnersAndMinecraftDescriptors() {
        Srg source = new Srg();
        source.putClassMapping("named/Stack", "srg/Stack");
        source.putFieldMapping("named/Stack", "item", "field_1");
        source.putMethodMapping("named/Stack", "copy", "(Lnamed/Stack;)Lnamed/Stack;", "func_1", "(Lsrg/Stack;)Lsrg/Stack;");
        Srg reverse = MixinMappings.inverse(source);
        assertEquals("item", reverse.fieldMappingsByOwningClass.get("srg/Stack").get("field_1"));
        assertEquals(new Srg.MethodEntry("copy", "(Lnamed/Stack;)Lnamed/Stack;"),
            reverse.methodMappingsByOwningClass.get("srg/Stack").get(new Srg.MethodEntry("func_1", "(Lsrg/Stack;)Lsrg/Stack;")));
    }

    @Test public void mergeRetainsOverloadsAndDoesNotMutateBase() throws Exception {
        Srg base = new Srg();
        base.putMethodMapping("Mixin", "shadow", "()I", "func_1", "()I");
        Srg release = MixinMappings.copy(base);
        merge(release, "MD: Mixin/shadow ()I Mixin/func_1 ()I\nMD: Mixin/shadow (I)I Mixin/func_2 (I)I\n");
        assertEquals(2, release.methodMappingsByOwningClass.get("Mixin").size());
        assertEquals(1, base.methodMappingsByOwningClass.get("Mixin").size());
    }

    @Test public void conflictingMappingsAndMalformedRecordsFail() throws Exception {
        Srg base = new Srg();
        base.putFieldMapping("Mixin", "item", "field_1");
        reject(base, "FD: Mixin/item Mixin/field_2", "Conflicting");
        reject(base, "MD: Mixin/copy ()LMinecraft; Mixin/func_1 ()LOther;", "descriptor");
        reject(base, "FD: Mixin/item Other/field_1", "owner");
        reject(base, "MD: broken", "Invalid");
    }

    @Test public void missingApOutputFailsInsteadOfReleasingWithoutMappings() throws Exception {
        Path file = Files.createTempFile("retromixin-missing", ".srg");
        Files.delete(file);
        try { MixinMappings.merge(new Srg(), file); fail("Accepted missing AP output"); }
        catch (java.io.IOException expected) { assertTrue(expected.getMessage().contains("Missing Mixin AP")); }
    }

    private static void reject(Srg target, String text, String message) throws Exception {
        try { merge(target, text); fail("Accepted " + text); }
        catch (IllegalArgumentException expected) { assertTrue(expected.getMessage(), expected.getMessage().contains(message)); }
    }

    private static void merge(Srg target, String text) throws Exception {
        Path file = Files.createTempFile("retromixin-mixin", ".srg");
        try {
            Files.write(file, text.getBytes(StandardCharsets.UTF_8));
            MixinMappings.merge(target, file);
        } finally { Files.delete(file); }
    }
}
