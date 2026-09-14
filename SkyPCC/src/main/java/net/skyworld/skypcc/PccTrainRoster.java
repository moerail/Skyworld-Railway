package net.skyworld.skypcc;

import java.util.*;
import net.skyworld.sta.api.v3.ConsistObservation;

/** Display inventory only. An unloaded train is not a current position report. */
final class PccTrainRoster {
    static List<Map<String, Object>> merge(List<Map<String, Object>> located,
            Collection<ConsistObservation> roster) {
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        for (var train : located) result.put((String) train.get("trainId"), train);
        for (var train : roster) {
            String id = train.train().toString();
            if (train.removed()) { result.remove(id); continue; }
            if (result.containsKey(id)) continue;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("trainId", id); row.put("name", train.name());
            row.put("memberCount", train.expectedMembers().size());
            row.put("quality", "AWAITING_POSITION"); row.put("stale", true);
            row.put("graphCurrent", false); row.put("mode", "unknown");
            result.put(id, Collections.unmodifiableMap(row));
        }
        return List.copyOf(result.values());
    }
}
