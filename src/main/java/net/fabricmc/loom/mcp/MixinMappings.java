package net.fabricmc.loom.mcp;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Strict SRG handling for AP outputs: never silently overwrite a mapping. */
public final class MixinMappings {
    private MixinMappings() { }

    public static Srg inverse(Srg source) {
        Srg result = new Srg();
        source.classMappings.forEach((from, to) -> put(result.classMappings, to, from, "class"));
        source.fieldMappingsByOwningClass.forEach((owner, fields) -> fields.forEach((from, to) -> {
            String targetOwner = source.classMappings.getOrDefault(owner, owner);
            java.util.Map<String, String> target = result.fieldMappingsByOwningClass.computeIfAbsent(targetOwner, k -> new java.util.LinkedHashMap<>());
            put(target, to, from, targetOwner);
        }));
        source.methodMappingsByOwningClass.forEach((owner, methods) -> methods.forEach((from, to) -> {
            String targetOwner = source.classMappings.getOrDefault(owner, owner);
            java.util.Map<Srg.MethodEntry, Srg.MethodEntry> target = result.methodMappingsByOwningClass.computeIfAbsent(targetOwner, k -> new java.util.LinkedHashMap<>());
            put(target, to, from, targetOwner);
        }));
        return result;
    }

    public static Srg copy(Srg source) {
        Srg result = new Srg();
        result.mergeWith(source);
        return result;
    }

    public static void merge(Srg target, Path file) throws IOException {
        if (!Files.isRegularFile(file)) throw new IOException("Missing Mixin AP mapping output: " + file);
        int lineNumber = 0;
        for (String line : Files.readAllLines(file, java.nio.charset.StandardCharsets.UTF_8)) {
            lineNumber++;
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
            String[] parts = trimmed.split("\\s+");
            String context = file + ":" + lineNumber;
            if (parts.length == 3 && parts[0].equals("CL:")) {
                put(target.classMappings, parts[1], parts[2], context);
            } else if (parts.length == 3 && parts[0].equals("FD:")) {
                String owner = owner(parts[1], context);
                checkOwner(target, owner, owner(parts[2], context), context);
                put(target.fieldMappingsByOwningClass.computeIfAbsent(owner, k -> new java.util.LinkedHashMap<>()), name(parts[1]), name(parts[2]), context);
            } else if (parts.length == 5 && parts[0].equals("MD:")) {
                String owner = owner(parts[1], context);
                checkOwner(target, owner, owner(parts[3], context), context);
                String descriptor = DescriptorMapper.map(parts[2], c -> target.classMappings.getOrDefault(c, c));
                if (!descriptor.equals(parts[4])) throw new IllegalArgumentException("Conflicting Mixin descriptor at " + context);
                put(target.methodMappingsByOwningClass.computeIfAbsent(owner, k -> new java.util.LinkedHashMap<>()),
                    new Srg.MethodEntry(name(parts[1]), parts[2]), new Srg.MethodEntry(name(parts[3]), parts[4]), context);
            } else {
                throw new IllegalArgumentException("Invalid Mixin AP SRG record at " + context + ": " + line);
            }
        }
    }

    private static String owner(String member, String context) {
        int slash = member.lastIndexOf('/');
        if (slash < 1 || slash == member.length() - 1) throw new IllegalArgumentException("Invalid member at " + context);
        return member.substring(0, slash);
    }

    private static String name(String member) { return member.substring(member.lastIndexOf('/') + 1); }

    private static void checkOwner(Srg target, String from, String to, String context) {
        if (!target.classMappings.getOrDefault(from, from).equals(to)) {
            throw new IllegalArgumentException("Conflicting Mixin owner at " + context + ": " + from + " -> " + to);
        }
    }

    private static <K, V> void put(java.util.Map<K, V> map, K key, V value, String context) {
        V previous = map.putIfAbsent(key, value);
        if (previous != null && !previous.equals(value)) {
            throw new IllegalArgumentException("Conflicting Mixin mapping at " + context + " for " + key + ": " + previous + " vs " + value);
        }
    }
}
