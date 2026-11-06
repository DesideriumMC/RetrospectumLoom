package net.fabricmc.loom.task;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.*;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.fabricmc.loom.mcp.Members;
import net.fabricmc.loom.util.ZipUtil;

final class DependencyRefmaps {
    private static final Gson GSON = new Gson();
    private static final Pattern SELECTOR = Pattern.compile("^(L[^;]+;)?([\\w$<>]+)([+*]|\\{[^}]*\\})?([(:].*)?$");

    static void remap(Path archive, Members fields, Members methods) throws IOException {
        try (FileSystem jar = ZipUtil.openFs(archive)) {
            Path root = jar.getPath("/");
            Set<Path> refmaps = new LinkedHashSet<>();
            // Configs can be registered explicitly at runtime, without a MixinConfigs manifest entry.
            try (Stream<Path> entries = Files.walk(root)) {
                for (Path path : entries.filter(Files::isRegularFile)
                        .filter(file -> file.toString().endsWith(".json")).collect(Collectors.toList())) {
                    JsonObject config;
                    try {
                        config = read(path);
                    } catch (JsonParseException ignored) {
                        continue;
                    }
                    if (config == null) continue;
                    JsonElement refmap = config.get("refmap");
                    if (refmap != null && refmap.isJsonPrimitive() && refmap.getAsJsonPrimitive().isString()) {
                        refmaps.add(root.resolve(refmap.getAsString()).normalize());
                    } else if (config.has("package") && (config.has("mixins") || config.has("client") || config.has("server"))) {
                        refmaps.add(root.resolve("mixin.refmap.json"));
                    }
                }
            }
            for (Path path : refmaps) {
                if (!Files.isRegularFile(path)) continue;
                try {
                    JsonObject refmap = read(path);
                    if (refmap == null) continue;
                    boolean changed = remapMappings(refmap.get("mappings"), fields, methods);
                    JsonElement data = refmap.get("data");
                    if (data != null && data.isJsonObject()) {
                        for (Map.Entry<String, JsonElement> context : data.getAsJsonObject().entrySet()) {
                            changed |= remapMappings(context.getValue(), fields, methods);
                        }
                    }
                    if (changed) {
                        try (Writer writer = Files.newBufferedWriter(path)) {
                            GSON.toJson(refmap, writer);
                        }
                    }
                } catch (JsonParseException e) {
                    throw new IOException("Invalid Mixin refmap " + path + " in " + archive, e);
                }
            }
        }
    }

    private static JsonObject read(Path path) throws IOException {
        try (Reader reader = Files.newBufferedReader(path)) {
            JsonElement json = GSON.fromJson(reader, JsonElement.class);
            return json != null && json.isJsonObject() ? json.getAsJsonObject() : null;
        }
    }

    private static boolean remapMappings(JsonElement mappings, Members fields, Members methods) {
        if (mappings == null || !mappings.isJsonObject()) return false;
        boolean changed = false;
        for (Map.Entry<String, JsonElement> mixin : mappings.getAsJsonObject().entrySet()) {
            if (!mixin.getValue().isJsonObject()) continue;
            for (Map.Entry<String, JsonElement> mapping : mixin.getValue().getAsJsonObject().entrySet()) {
                JsonElement value = mapping.getValue();
                if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) continue;
                String selector = value.getAsString();
                Matcher matcher = SELECTOR.matcher(selector);
                if (!matcher.matches()) continue;
                String name = matcher.group(2);
                String descriptor = matcher.group(4);
                Members.Entry entry = descriptor != null && descriptor.startsWith(":")
                        ? fields.remapSrg(name) : methods.remapSrg(name);
                if (entry == null && descriptor == null) entry = fields.remapSrg(name);
                if (entry != null && !name.equals(entry.remappedName)) {
                    mixin.getValue().getAsJsonObject().addProperty(mapping.getKey(),
                            selector.substring(0, matcher.start(2)) + entry.remappedName + selector.substring(matcher.end(2)));
                    changed = true;
                }
            }
        }
        return changed;
    }
}
