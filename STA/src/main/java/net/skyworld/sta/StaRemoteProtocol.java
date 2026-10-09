package net.skyworld.sta;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.skyworld.sta.api.v5.RailNetworkService;
import net.skyworld.sta.api.v5.SwitchControlService;

/** Validation shared by the TLS gateway and its contract tests. */
final class StaRemoteProtocol {
    static final String VERSION = "STA_REMOTE/1";
    private StaRemoteProtocol() { }

    static boolean authenticate(Map<String, byte[]> admins, String admin, String token) {
        if (admin == null || token == null || token.length() < 32 || token.length() > 256) return false;
        byte[] expected = admins.get(admin);
        byte[] actual = sha256(token);
        return MessageDigest.isEqual(expected == null ? new byte[32] : expected, actual) && expected != null;
    }

    static byte[] digest(String hex) {
        if (hex == null || !hex.matches("[0-9a-fA-F]{64}")) throw new IllegalArgumentException("Invalid token digest");
        return HexFormat.of().parseHex(hex);
    }

    private static byte[] sha256(String token) {
        try { return MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }

    static String required(JsonObject object, String key) {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())
            throw new IllegalArgumentException("Missing " + key);
        String text = value.getAsString();
        if (text.length() > 256) throw new IllegalArgumentException("Oversized " + key);
        return text;
    }

    static void keys(JsonObject object, String... expected) {
        if (!object.keySet().equals(Set.of(expected))) throw new IllegalArgumentException("Invalid fields");
    }

    static JsonObject parse(String line) {
        try { return JsonParser.parseString(line).getAsJsonObject(); }
        catch (RuntimeException ex) { throw new IllegalArgumentException("Invalid JSON", ex); }
    }

    record SwitchView(UUID id, long revision, String state, SwitchControlService.Position position) { }

    static SwitchView switchView(RailNetworkService network, UUID id) {
        if (network == null || id == null) throw new IllegalArgumentException("SERVICE_UNAVAILABLE");
        try {
            JsonObject graph = JsonParser.parseString(network.graphJson()).getAsJsonObject();
            long revision = graph.get("revision").getAsLong();
            if (revision < 0 || revision != network.graphRevision()) throw new IllegalArgumentException("GRAPH_CHANGED");
            for (JsonElement entry : graph.getAsJsonArray("nodes")) {
                JsonObject node = entry.getAsJsonObject();
                if (!id.toString().equals(node.get("id").getAsString())) continue;
                if (!"switch".equals(node.get("type").getAsString())) throw new IllegalArgumentException("NOT_SWITCH");
                String state = node.get("state").getAsString().toLowerCase(java.util.Locale.ROOT);
                if (!Set.of("straight", "diverging").contains(state)) throw new IllegalArgumentException("SWITCH_UNKNOWN");
                JsonObject p = node.getAsJsonObject("rail");
                if (p == null) throw new IllegalArgumentException("POSITION_REQUIRED");
                var position = new SwitchControlService.Position(p.get("world").getAsString(),
                        p.get("x").getAsBigDecimal().intValueExact(),
                        p.get("y").getAsBigDecimal().intValueExact(),
                        p.get("z").getAsBigDecimal().intValueExact());
                return new SwitchView(id, revision, state, position);
            }
            throw new IllegalArgumentException("NOT_SWITCH");
        } catch (IllegalArgumentException ex) { throw ex; }
        catch (RuntimeException ex) { throw new IllegalArgumentException("GRAPH_UNAVAILABLE", ex); }
    }
}
