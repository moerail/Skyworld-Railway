package net.skyworld.skypcc;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.skyworld.sta.api.v6.OperationalAuthorityService;

public final class SrGatewayTest {
    public static void main(String[] args) {
        var gateway = new SrGateway();
        UUID train = UUID.randomUUID(), target = UUID.randomUUID(), requestId = UUID.randomUUID();
        var request = new SrGateway.Request(requestId, train, target, 7);
        var jobs = new ArrayList<Runnable>();
        OperationalAuthorityService service = new OperationalAuthorityService() {
            public Snapshot operationalSnapshot() { return null; }
            public String approveSr(UUID trainId, UUID targetNodeId, String actor) {
                assert train.equals(trainId) && target.equals(targetNodeId) && actor.equals("SkyPCC");
                return "APPROVED";
            }
        };
        assert gateway.submit(request, 8, Set.of(train), service, jobs::add, 1000)
                .reason().equals("GRAPH_CHANGED");
        assert gateway.submit(request, 7, Set.of(), service, jobs::add, 1000)
                .reason().equals("NO_PENDING_SR");
        assert gateway.submit(request, 7, Set.of(train), service, jobs::add, 1000)
                .status().equals("PENDING");
        assert jobs.size() == 1;
        assert gateway.submit(request, 7, Set.of(), service, jobs::add, 1001)
                .status().equals("PENDING") && jobs.size() == 1;
        jobs.getFirst().run();
        assert gateway.submit(request, 7, Set.of(), service, jobs::add, 1002)
                .status().equals("APPLIED") && jobs.size() == 1;
        assert gateway.submit(new SrGateway.Request(requestId, train, UUID.randomUUID(), 7), 7,
                Set.of(train), service, jobs::add, 1002).reason().equals("REQUEST_ID_CONFLICT");
        System.out.println("SR gateway revision, pending, idempotence and async approval passed");
    }
}
