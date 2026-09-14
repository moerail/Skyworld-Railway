package net.skyworld.sta.api.v2;

import com.google.gson.*;
import java.util.*;
import net.skyworld.sta.api.v1.TrainTelemetrySnapshot;

/** Shared JSON codec for diagnostics/web. In-process transport uses immutable Java records. */
public final class StaJson {
    private static final Gson GSON = new GsonBuilder().serializeNulls().disableHtmlEscaping().create();
    private StaJson() {}
    public static JsonObject tree(StaMessage m) {
        var h = m.header();
        JsonObject root = new JsonObject(), header = new JsonObject();
        header.addProperty("protocol", "STA");
        header.addProperty("M_VERSION", h.version()); header.addProperty("NID_MESSAGE", h.kind().id);
        header.addProperty("NID_SOURCE", h.source().name()); header.addProperty("NID_SESSION", h.sessionId().toString());
        header.addProperty("N_SEQUENCE", h.sequence()); header.addProperty("T_EMITTED_MS", h.emittedAtMillis());
        header.addProperty("NID_TRAIN", h.trainId().toString()); root.add("header", header);
        JsonArray packets = new JsonArray();
        if (m.physical() != null) {
            JsonObject p = GSON.toJsonTree(m.physical()).getAsJsonObject();
            p.addProperty("NID_PACKET", 2001); p.addProperty("Q_REQUIRED", true);
            p.addProperty("positionReference", "ACTIVE_LEADER_CART_CENTRE");
            p.addProperty("lengthQuality", "NOMINAL_CENTRE_SPAN");
            p.addProperty("speedTimebase", "NOMINAL_20_TPS"); packets.add(p);
        }
        if (m.tracking() != null) {
            JsonObject p = GSON.toJsonTree(m.tracking()).getAsJsonObject();
            p.addProperty("NID_PACKET", 2002); p.addProperty("Q_REQUIRED", true); packets.add(p);
        }
        root.add("packets", packets); return root;
    }
    public static String encode(StaMessage message) { return GSON.toJson(tree(message)); }
    public static StaMessage decode(String json) {
        if (json == null || json.length() > 65536) throw new IllegalArgumentException("Invalid message size");
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject(), h = root.getAsJsonObject("header");
            if (!"STA".equals(h.get("protocol").getAsString())) throw new IllegalArgumentException("protocol");
            var header = new StaMessage.Header(Math.toIntExact(integer(h,"M_VERSION")), StaMessage.Kind.of(Math.toIntExact(integer(h,"NID_MESSAGE"))),
                    StaMessage.Source.valueOf(h.get("NID_SOURCE").getAsString()), UUID.fromString(h.get("NID_SESSION").getAsString()),
                    integer(h,"N_SEQUENCE"), integer(h,"T_EMITTED_MS"), UUID.fromString(h.get("NID_TRAIN").getAsString()));
            StaMessage.Physical physical = null; StaMessage.Tracking tracking = null;
            Set<Integer> seen = new HashSet<>();
            for (JsonElement element : root.getAsJsonArray("packets")) {
                JsonObject p = element.getAsJsonObject(); int id = Math.toIntExact(integer(p,"NID_PACKET"));
                if (!seen.add(id)) throw new IllegalArgumentException("Duplicate packet");
                switch (id) {
                    case 2001 -> {
                        constant(p,"positionReference","ACTIVE_LEADER_CART_CENTRE");
                        constant(p,"lengthQuality","NOMINAL_CENTRE_SPAN");
                        constant(p,"speedTimebase","NOMINAL_20_TPS");
                        JsonObject s = p.getAsJsonObject("state");
                        for (String k : List.of("sequence","observedAtMillis","memberCount","railX","railY","railZ")) integer(s,k);
                        for (String k : List.of("memberCount","railX","railY","railZ")) Math.toIntExact(integer(s,k));
                        for (String k : List.of("x","y","z","motionX","motionY","motionZ","speedMetersPerSecond","lengthMeters")) finite(s,k);
                        for (String k : List.of("trainId","trainName","world","mode","driverName")) string(s,k);
                        for (String k : List.of("moving","reversed")) bool(s,k);
                        net.skyworld.sta.api.v1.TrainMode.valueOf(s.get("mode").getAsString());
                        if (s.has("trainNumber") && !s.get("trainNumber").isJsonNull()) string(s, "trainNumber");
                        if (s.has("cab") && !s.get("cab").isJsonNull()) {
                            JsonObject cab = s.getAsJsonObject("cab");
                            for (String k : List.of("atpMode", "reverser")) string(cab, k);
                            for (String k : List.of("powerNotch", "brakeNotch")) Math.toIntExact(integer(cab, k));
                            for (String k : List.of("emergencyBrake", "brakeHold")) bool(cab, k);
                        }
                        finite(p,"blocksPerMeter");
                        physical = new StaMessage.Physical(GSON.fromJson(s, TrainTelemetrySnapshot.class), p.get("blocksPerMeter").getAsDouble());
                    }
                    case 2002 -> {
                        for (String k : List.of("telemetrySequence","receivedAtMillis","resolvedAtMillis","staleAfterMillis","expireAfterMillis","graphRevision")) integer(p,k);
                        for (String k : List.of("telemetrySessionId","quality","reason")) string(p,k);
                        if (!p.has("position")) throw new IllegalArgumentException("position must be present or null");
                        if (!p.get("position").isJsonNull()) {
                            JsonObject pos = p.getAsJsonObject("position");
                            for (String k : List.of("graphRevision","measuredAtMillis")) integer(pos,k);
                            for (String k : List.of("edgeOffsetMeters","edgeLengthMeters")) finite(pos,k);
                            for (String k : List.of("edgeId","edgeFromNodeId","edgeToNodeId","line")) string(pos,k);
                            for (String k : List.of("graphCurrent","stale")) bool(pos,k);
                            if (!pos.has("mileageMeters")) throw new IllegalArgumentException("mileageMeters must be present or null");
                            if (!pos.get("mileageMeters").isJsonNull()) finite(pos,"mileageMeters");
                        }
                        tracking = GSON.fromJson(p, StaMessage.Tracking.class);
                    }
                    default -> { if (!p.has("Q_REQUIRED") || p.get("Q_REQUIRED").getAsBoolean()) throw new IllegalArgumentException("Unknown required packet"); }
                }
            }
            return new StaMessage(header, physical, tracking);
        } catch (RuntimeException ex) { throw new IllegalArgumentException("Invalid STA message", ex); }
    }
    private static long integer(JsonObject o, String key) {
        JsonPrimitive p = o.getAsJsonPrimitive(key);
        if (p == null || !p.isNumber()) throw new IllegalArgumentException("Numeric field required: " + key);
        return p.getAsBigDecimal().longValueExact();
    }
    private static void constant(JsonObject o, String key, String expected) {
        if (!o.has(key) || !expected.equals(o.get(key).getAsString())) throw new IllegalArgumentException("Unsupported " + key);
    }
    private static void finite(JsonObject o, String key) {
        JsonPrimitive p=o.getAsJsonPrimitive(key);
        if(p==null || !p.isNumber() || !Double.isFinite(p.getAsDouble())) throw new IllegalArgumentException("Finite number required: "+key);
    }
    private static void string(JsonObject o, String key) {
        JsonPrimitive p=o.getAsJsonPrimitive(key);
        if(p==null || !p.isString()) throw new IllegalArgumentException("String required: "+key);
    }
    private static void bool(JsonObject o, String key) {
        JsonPrimitive p=o.getAsJsonPrimitive(key);
        if(p==null || !p.isBoolean()) throw new IllegalArgumentException("Boolean required: "+key);
    }
}
