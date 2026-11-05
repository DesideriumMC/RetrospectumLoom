package net.fabricmc.loom.mcp;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loom.util.StringInterner;
import org.junit.Test;
import static org.junit.Assert.*;

public class SrgSnapshotTest {
    @Test public void snapshotIsOrderIndependentAndRoundTripsMappedOwnersAndDescriptors() throws Exception {
        Srg first = new Srg();
        first.putClassMapping("named/Owner", "srg/Owner");
        first.putClassMapping("named/Value", "srg/Value");
        first.putFieldMapping("named/Owner", "value", "field_1");
        first.putMethodMapping("named/Owner", "get", "()Lnamed/Value;", "func_1", "()Lsrg/Value;");
        Srg reordered = new Srg();
        reordered.putClassMapping("named/Value", "srg/Value");
        reordered.putClassMapping("named/Owner", "srg/Owner");
        reordered.putMethodMapping("named/Owner", "get", "()Lnamed/Value;", "func_1", "()Lsrg/Value;");
        reordered.putFieldMapping("named/Owner", "value", "field_1");
        assertEquals(SrgSnapshot.lines(first), SrgSnapshot.lines(reordered));
        Path file = Files.createTempFile("retrospectum-mappings", ".srg");
        try (StringInterner strings = new StringInterner()) {
            Files.write(file, SrgSnapshot.lines(first), StandardCharsets.UTF_8);
            Srg restored = new Srg().read(file, strings);
            assertEquals(first.classMappings, restored.classMappings);
            assertEquals(first.fieldMappingsByOwningClass, restored.fieldMappingsByOwningClass);
            assertEquals(first.methodMappingsByOwningClass, restored.methodMappingsByOwningClass);
            restored.putFieldMapping("named/Owner", "other", "field_2");
            assertNotEquals(SrgSnapshot.lines(first), SrgSnapshot.lines(restored));
        } finally { Files.deleteIfExists(file); }
    }
}
