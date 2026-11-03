package net.fabricmc.loom.task.fernflower;

import org.junit.Test;
import static org.junit.Assert.*;

public class SourceReadabilityTest {
    @Test public void normalizesOnlyArrayLengthsAndPreservesLines() {
        String input = "char c = '\u8000'; String s = \"new int['\u8000']\";\n"
            + "// new int['\u8000']\nint[] a = new int[ /* size */ '\u8000' ];\n"
            + "int[][] b = new int['\u8000']['\u8000']; char[] chars = {'\u8000'};";
        String output = SourceReadability.normalize(input);
        assertTrue(output.contains("char c = '\u8000'"));
        assertTrue(output.contains("\"new int['\u8000']\""));
        assertTrue(output.contains("// new int['\u8000']"));
        assertTrue(output.contains("new int[ /* size */ 32768 ]"));
        assertTrue(output.contains("new int[32768][32768]"));
        assertTrue(output.contains("char[] chars = {'\u8000'}"));
        assertEquals(input.chars().filter(c -> c == '\n').count(), output.chars().filter(c -> c == '\n').count());
    }
    @Test public void handlesUnicodeEscapeWithoutChangingAsciiOrExpressions() {
        String escape = "'" + "\\" + "u8000'";
        assertEquals("new int[32768]", SourceReadability.normalize("new int[" + escape + "]"));
        String unchanged = "new int['a']; new int['\u8000' + 1]; Object x = new Example('\u8000');";
        assertEquals(unchanged, SourceReadability.normalize(unchanged));
    }
}
