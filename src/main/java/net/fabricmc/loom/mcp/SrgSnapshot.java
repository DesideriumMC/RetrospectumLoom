package net.fabricmc.loom.mcp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Canonical SRG records for declaring the provider graph's mapping state as task inputs. */
public final class SrgSnapshot {
    private SrgSnapshot() {}

    public static List<String> lines(Srg mappings) {
        List<String> records = new ArrayList<>();
        mappings.classMappings.forEach((from, to) -> records.add("CL: " + from + " " + to));
        mappings.fieldMappingsByOwningClass.forEach((owner, fields) -> {
            String mappedOwner = mappings.classMappings.getOrDefault(owner, owner);
            fields.forEach((from, to) -> records.add("FD: " + owner + "/" + from + " " + mappedOwner + "/" + to));
        });
        mappings.methodMappingsByOwningClass.forEach((owner, methods) -> {
            String mappedOwner = mappings.classMappings.getOrDefault(owner, owner);
            methods.forEach((from, to) -> records.add("MD: " + owner + "/" + from.name + " " + from.descriptor
                + " " + mappedOwner + "/" + to.name + " " + to.descriptor));
        });
        Collections.sort(records);
        return records;
    }
}
