package net.fabricmc.loom.task.fernflower;

import java.util.ArrayList;
import java.util.List;

/** Cosmetic, line-preserving normalization of char-inferred primitive array lengths. */
final class SourceReadability {
    private static final class Token {
        final int start, end;
        final String text;
        Token(String source, int start, int end) {
            this.start = start; this.end = end; this.text = source.substring(start, end);
        }
    }

    static String normalize(String source) {
        List<Token> tokens = new ArrayList<>();
        for (int i = 0; i < source.length();) {
            char c = source.charAt(i);
            if (Character.isWhitespace(c)) { i++; continue; }
            if (source.startsWith("//", i)) {
                int end = source.indexOf('\n', i + 2); i = end < 0 ? source.length() : end; continue;
            }
            if (source.startsWith("/*", i)) {
                int end = source.indexOf("*/", i + 2); i = end < 0 ? source.length() : end + 2; continue;
            }
            int start = i++;
            if (c == '\'' || c == '"') {
                while (i < source.length()) {
                    char next = source.charAt(i++);
                    if (next == '\\' && i < source.length()) i++;
                    else if (next == c) break;
                }
            } else if (Character.isJavaIdentifierStart(c)) {
                while (i < source.length() && Character.isJavaIdentifierPart(source.charAt(i))) i++;
            }
            tokens.add(new Token(source, start, i));
        }
        StringBuilder result = new StringBuilder();
        int copied = 0;
        for (int i = 0; i + 4 < tokens.size(); i++) {
            if (!tokens.get(i).text.equals("new") || !isPrimitive(tokens.get(i + 1).text)) continue;
            int dimension = i + 2;
            while (dimension + 2 < tokens.size() && tokens.get(dimension).text.equals("[")) {
                Token value = tokens.get(dimension + 1);
                if (!tokens.get(dimension + 2).text.equals("]")) break;
                Integer number = nonAsciiChar(value.text);
                if (number != null) {
                    result.append(source, copied, value.start).append(number);
                    copied = value.end;
                }
                dimension += 3;
            }
        }
        return copied == 0 ? source : result.append(source, copied, source.length()).toString();
    }

    private static boolean isPrimitive(String text) {
        return text.matches("boolean|byte|short|char|int|long|float|double");
    }

    private static Integer nonAsciiChar(String text) {
        int value;
        if (text.length() == 3 && text.charAt(0) == '\'' && text.charAt(2) == '\'') value = text.charAt(1);
        else if (text.matches("'\\\\u+[0-9a-fA-F]{4}'")) value = Integer.parseInt(text.substring(text.length() - 5, text.length() - 1), 16);
        else return null;
        return value > 127 ? value : null;
    }
}
