package net.skyworld.sta;

import java.util.Map;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import net.skyworld.sta.api.v5.RailNetworkService;

public final class StaRemoteProtocolTest {
    private static final UUID SWITCH = UUID.fromString("10000000-0000-4000-8000-000000000001");
    private static RailNetworkService network(long revision) {
        return new RailNetworkService() {
            public long graphRevision() { return revision; }
            public double blocksPerMeter() { return 1; }
            public String graphJson() {
                return """
                        {"schemaVersion":3,"revision":12,"nodes":[
                          {"id":"10000000-0000-4000-8000-000000000001","type":"switch",
                           "state":"straight","rail":{"world":"world","x":7,"y":64,"z":-9}}
                        ],"edges":[]}
                        """;
            }
            public Navigation query(RailQuery ignored) { return null; }
        };
    }
    public static void main(String[] args) throws Exception {
        String token = "a-test-only-admin-token-that-is-long-enough";
        String hex = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(token.getBytes(StandardCharsets.UTF_8)));
        byte[] digest = StaRemoteProtocol.digest(hex);
        assert StaRemoteProtocol.authenticate(Map.of("alice", digest), "alice", token);
        assert !StaRemoteProtocol.authenticate(Map.of("alice", digest), "bob", token);
        assert !StaRemoteProtocol.authenticate(Map.of("alice", digest), "alice", "short");
        var view = StaRemoteProtocol.switchView(network(12), SWITCH);
        assert view.id().equals(SWITCH) && view.revision() == 12;
        assert view.state().equals("straight") && view.position().x() == 7 && view.position().z() == -9;
        try { StaRemoteProtocol.switchView(network(13), SWITCH); throw new AssertionError("Stale graph accepted"); }
        catch (IllegalArgumentException expected) { assert expected.getMessage().equals("GRAPH_CHANGED"); }
        try { StaRemoteProtocol.switchView(network(12), UUID.randomUUID()); throw new AssertionError("Unknown switch accepted"); }
        catch (IllegalArgumentException expected) { assert expected.getMessage().equals("NOT_SWITCH"); }
        System.out.println("STA Remote authentication and graph-bound switch preview passed");
    }
}
