package net.skyworld.sta;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;
import org.bukkit.configuration.ConfigurationSection;
import net.skyworld.sta.api.v5.RailNetworkService;
import net.skyworld.sta.api.v5.PccProjection;
import net.skyworld.sta.api.v5.ShadowAuthorityService;
import net.skyworld.sta.api.v5.StaMessage;
import net.skyworld.sta.api.v5.SwitchControlService;
import net.skyworld.sta.api.v5.TrackingService;
import net.skyworld.sta.api.v6.OperationalAuthorityService;

/** TLS-only desktop dispatcher transport. The registered STCS services remain the final authority. */
final class StaRemoteServer implements AutoCloseable {
    private static final Gson JSON = new Gson();
    private static final int MAX_REQUEST_BYTES = 4096;
    private final StaPlugin plugin;
    private final SSLServerSocket listener;
    private final Map<String, byte[]> admins;
    private final Semaphore slots = new Semaphore(8);
    private final Set<SSLSocket> sockets = ConcurrentHashMap.newKeySet();
    private final ExecutorService clients = Executors.newFixedThreadPool(8, runnable -> {
        Thread thread = new Thread(runnable, "STA-remote-client");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Map<UUID, SrJob> srJobs = new HashMap<>();
    private final Thread acceptThread;

    private record SrJob(UUID train, UUID target, long revision, String actor,
                         CompletableFuture<String> result, long at) { }
    private StaRemoteServer(StaPlugin plugin, SSLServerSocket listener, Map<String, byte[]> admins) {
        this.plugin = plugin;
        this.listener = listener;
        this.admins = admins;
        acceptThread = new Thread(this::acceptLoop, "STA-remote-accept");
        acceptThread.setDaemon(true);
        acceptThread.start();
    }

    static StaRemoteServer start(StaPlugin plugin) throws Exception {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("remote.admins");
        if (section == null || section.getKeys(false).isEmpty())
            throw new IllegalArgumentException("Configure at least one remote.admins token digest");
        Map<String, byte[]> admins = new HashMap<>();
        for (String name : section.getKeys(false)) {
            if (!name.matches("[A-Za-z0-9_-]{1,32}")) throw new IllegalArgumentException("Invalid administrator name");
            admins.put(name, StaRemoteProtocol.digest(section.getString(name)));
        }
        String variable = plugin.getConfig().getString("remote.keystore-password-env", "");
        if (variable == null || !variable.matches("[A-Za-z_][A-Za-z0-9_]{0,63}"))
            throw new IllegalArgumentException("Invalid keystore password environment variable");
        String password = System.getenv(variable);
        if (password == null || password.isEmpty()) throw new IllegalArgumentException("Missing keystore password environment variable");
        String filename = plugin.getConfig().getString("remote.keystore", "");
        if (filename == null || filename.isBlank()) throw new IllegalArgumentException("Missing keystore path");
        Path relative = Path.of(filename);
        if (relative.isAbsolute()) throw new IllegalArgumentException("Keystore must be within the plugin data directory");
        Path directory = plugin.getDataFolder().toPath().toAbsolutePath().normalize();
        Path file = directory.resolve(relative).normalize();
        if (!file.startsWith(directory) || !Files.isRegularFile(file))
            throw new IllegalArgumentException("Keystore not found in plugin data directory");
        KeyStore keystore = KeyStore.getInstance("PKCS12");
        try (var input = Files.newInputStream(file)) { keystore.load(input, password.toCharArray()); }
        KeyManagerFactory managers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        managers.init(keystore, password.toCharArray());
        SSLContext tls = SSLContext.getInstance("TLSv1.3");
        tls.init(managers.getKeyManagers(), null, null);
        String address = plugin.getConfig().getString("remote.bind-address", "127.0.0.1");
        int port = plugin.getConfig().getInt("remote.port", 8766);
        if (address == null || address.isBlank() || port < 1 || port > 65535)
            throw new IllegalArgumentException("Invalid bind address or port");
        SSLServerSocket listener = (SSLServerSocket) tls.getServerSocketFactory().createServerSocket();
        try {
            listener.setEnabledProtocols(new String[]{"TLSv1.3"});
            listener.bind(new InetSocketAddress(address, port), 8);
            StaRemoteServer server = new StaRemoteServer(plugin, listener, Map.copyOf(admins));
            plugin.getLogger().info("STA Remote TLS listening on " + address + ":" + port + " (8 clients max)");
            return server;
        } catch (Exception ex) {
            listener.close();
            throw ex;
        }
    }

    private void acceptLoop() {
        while (!closed.get()) {
            try {
                SSLSocket socket = (SSLSocket) listener.accept();
                if (!slots.tryAcquire()) { socket.close(); continue; }
                sockets.add(socket);
                try { clients.execute(() -> {
                    try { handle(socket); }
                    finally {
                        sockets.remove(socket);
                        try { socket.close(); } catch (IOException ignored) { }
                        slots.release();
                    }
                }); } catch (RuntimeException ex) {
                    sockets.remove(socket); slots.release(); socket.close();
                }
            } catch (IOException ex) {
                if (!closed.get()) plugin.getLogger().warning("STA Remote accept failed: " + ex.getMessage());
            }
        }
    }

    private void handle(SSLSocket socket) {
        try {
            socket.setEnabledProtocols(new String[]{"TLSv1.3"});
            socket.setSoTimeout(10_000);
            socket.startHandshake();
            InputStream input = socket.getInputStream();
            BufferedWriter output = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
            String first = readLine(input);
            if (first == null) return;
            JsonObject auth = StaRemoteProtocol.parse(first);
            StaRemoteProtocol.keys(auth, "type", "admin", "token");
            if (!"auth".equals(StaRemoteProtocol.required(auth, "type"))) return;
            String admin = StaRemoteProtocol.required(auth, "admin");
            if (!StaRemoteProtocol.authenticate(admins, admin, StaRemoteProtocol.required(auth, "token"))) {
                send(output, result(null, "REJECTED", "AUTH_REQUIRED", null));
                return;
            }
            plugin.getLogger().info("STA Remote administrator connected: " + admin);
            send(output, result(null, "OK", StaRemoteProtocol.VERSION, null));
            socket.setSoTimeout(30_000);
            long lastWrite = 0;
            while (!closed.get()) {
                String line = readLine(input);
                if (line == null) return;
                JsonObject request;
                try { request = StaRemoteProtocol.parse(line); }
                catch (RuntimeException ex) { send(output, result(null, "REJECTED", "INVALID_REQUEST", null)); continue; }
                String id = null;
                try {
                    id = UUID.fromString(StaRemoteProtocol.required(request, "id")).toString();
                    String type = StaRemoteProtocol.required(request, "type");
                    if (Set.of("switch.set", "sr.approve", "ma.revoke").contains(type)) {
                        long now = System.nanoTime();
                        if (now - lastWrite < TimeUnit.SECONDS.toNanos(1)) {
                            send(output, result(id, "REJECTED", "RATE_LIMIT", null)); continue;
                        }
                        lastWrite = now;
                    }
                    JsonObject response = process(id, type, request, admin);
                    send(output, response);
                    if (Set.of("switch.set", "sr.approve", "ma.revoke").contains(type)) {
                        String target = type.equals("switch.set") ? StaRemoteProtocol.required(request, "switchId")
                                : StaRemoteProtocol.required(request, "trainId");
                        plugin.getLogger().info("STA Remote actor=" + admin + " operation=" + type + " request=" + id
                                + " target=" + target + " status=" + response.get("status").getAsString()
                                + " reason=" + response.get("reason").getAsString());
                    }
                } catch (IllegalArgumentException ex) {
                    send(output, result(id, "REJECTED", ex.getMessage() == null ? "INVALID_REQUEST" : ex.getMessage(), null));
                } catch (Exception ex) {
                    send(output, result(id, "FAILED", "SERVICE_ERROR", null));
                    plugin.getLogger().warning("STA Remote request failed: " + ex.getClass().getSimpleName());
                }
            }
        } catch (Exception ex) {
            // Disconnect, timeout and TLS failures are per-client; never affect the in-process STA services.
        }
    }

    private JsonObject process(String id, String type, JsonObject req, String admin) throws Exception {
        var services = plugin.getServer().getServicesManager();
        return switch (type) {
            case "ma.revoke" -> {
                StaRemoteProtocol.keys(req,"id","type","requestId","trainId");
                var service=services.load(OperationalAuthorityService.class);
                if(service==null)throw new IllegalArgumentException("SERVICE_UNAVAILABLE");
                String reason=service.revokeMa(UUID.fromString(StaRemoteProtocol.required(req,"requestId")),
                        UUID.fromString(StaRemoteProtocol.required(req,"trainId")),"STA Remote:"+admin)
                        .toCompletableFuture().getNow(null);
                yield result(id,reason==null?"PENDING":reason.equals("REVOKED_TR")?"APPLIED":"REJECTED",
                        reason==null?"WAITING_TR":reason,null);
            }
            case "events.list" -> {
                StaRemoteProtocol.keys(req, "id", "type");
                var source = services.load(net.skyworld.sta.api.v3.RailwayEventService.class);
                if (source == null) throw new IllegalArgumentException("SERVICE_UNAVAILABLE");
                yield result(id, "OK", "RAILWAY_EVENTS", JSON.toJsonTree(source.history()));
            }
            case "graph.get" -> {
                StaRemoteProtocol.keys(req, "id", "type");
                var network = services.load(RailNetworkService.class);
                if (network == null) throw new IllegalArgumentException("SERVICE_UNAVAILABLE");
                String graph = network.graphJson();
                if (graph == null || graph.length() > 8_000_000) throw new IllegalArgumentException("GRAPH_UNAVAILABLE");
                yield result(id, "OK", "GRAPH", JsonParser.parseString(graph));
            }
            case "switch.inspect" -> {
                StaRemoteProtocol.keys(req, "id", "type", "switchId");
                var view = StaRemoteProtocol.switchView(services.load(RailNetworkService.class),
                        UUID.fromString(StaRemoteProtocol.required(req, "switchId")));
                yield result(id, "OK", "SWITCH", JSON.toJsonTree(view));
            }
            case "trains.list" -> {
                StaRemoteProtocol.keys(req, "id", "type");
                var tracking = services.load(TrackingService.class);
                if (tracking == null || !tracking.sourceAvailable())
                    throw new IllegalArgumentException("SERVICE_UNAVAILABLE");
                long now = System.currentTimeMillis();
                var trains = tracking.snapshots().stream()
                        .filter(message -> message.header().kind() == StaMessage.Kind.TRACK_REPORT)
                        .map(message -> PccProjection.train(message, now)).toList();
                var roster=services.load(net.skyworld.sta.api.v3.ConsistObservationService.class);
                var byId=new java.util.HashMap<String,net.skyworld.sta.api.v3.ConsistObservation>();
                if(roster!=null) for(var c:roster.consistObservations())if(!c.removed())byId.put(c.train().toString(),c);
                var enriched=new java.util.ArrayList<java.util.Map<String,Object>>();
                var located=new java.util.HashSet<String>();
                for(var row:trains) {
                    var copy=new java.util.LinkedHashMap<String,Object>(row);
                    var trainKey=String.valueOf(row.get("trainId"));located.add(trainKey);
                    var c=byId.get(trainKey);if(c!=null)copy.put("integrity",c.integrity());
                    enriched.add(copy);
                }
                for(var c:byId.values())if(!located.contains(c.train().toString())) {
                    var row=new java.util.LinkedHashMap<String,Object>();
                    row.put("trainId",c.train().toString());row.put("name",c.name());
                    row.put("memberCount",c.expectedMembers().size());row.put("quality","AWAITING_POSITION");
                    row.put("stale",true);row.put("graphCurrent",false);row.put("mode","unknown");
                    row.put("integrity",c.integrity());enriched.add(row);
                }
                yield result(id, "OK", "TRAINS", JSON.toJsonTree(enriched));
            }
            case "shadow.get" -> {
                StaRemoteProtocol.keys(req, "id", "type");
                var network = services.load(RailNetworkService.class);
                var source = services.load(ShadowAuthorityService.class);
                if (network == null || source == null) throw new IllegalArgumentException("SERVICE_UNAVAILABLE");
                var view = source.snapshot();
                long now = System.currentTimeMillis();
                if (view == null || !view.status().equals("SHADOW") || !view.simulationOnly()
                        || view.executable() || view.graphRevision() != network.graphRevision()
                        || view.emittedAtMillis() > now || now - view.emittedAtMillis() > 1500)
                    throw new IllegalArgumentException("SHADOW_STALE");
                yield result(id, "OK", "SHADOW", JSON.toJsonTree(view));
            }
            case "switch.set" -> {
                StaRemoteProtocol.keys(req, "id", "type", "switchId", "graphRevision", "expectedState", "targetState");
                UUID switchId = UUID.fromString(StaRemoteProtocol.required(req, "switchId"));
                long revision = req.get("graphRevision").getAsBigDecimal().longValueExact();
                String expected = StaRemoteProtocol.required(req, "expectedState");
                String target = StaRemoteProtocol.required(req, "targetState");
                var view = StaRemoteProtocol.switchView(services.load(RailNetworkService.class), switchId);
                if (view.revision() != revision || !view.state().equals(expected))
                    throw new IllegalArgumentException("GRAPH_OR_STATE_CHANGED");
                var service = services.load(SwitchControlService.class);
                if (service == null) throw new IllegalArgumentException("SERVICE_UNAVAILABLE");
                var command = new SwitchControlService.Request(UUID.fromString(id), switchId, revision,
                        expected, target, view.position());
                CompletableFuture<SwitchControlService.Reply> done = new CompletableFuture<>();
                plugin.getServer().getAsyncScheduler().runNow(plugin, task -> {
                    try { service.change(command).whenComplete((reply, error) -> {
                        if (error != null || reply == null) done.completeExceptionally(
                                error == null ? new IllegalStateException("No reply") : error);
                        else done.complete(reply);
                    }); } catch (RuntimeException ex) { done.completeExceptionally(ex); }
                });
                var reply = done.get(45, TimeUnit.SECONDS);
                yield result(id, reply.status(), reply.reason(), null);
            }
            case "switch.status" -> {
                StaRemoteProtocol.keys(req, "id", "type", "requestId");
                var service = services.load(SwitchControlService.class);
                if (service == null) throw new IllegalArgumentException("SERVICE_UNAVAILABLE");
                var reply = service.status(UUID.fromString(StaRemoteProtocol.required(req, "requestId")));
                yield reply == null ? result(id, "REJECTED", "UNKNOWN_REQUEST", null)
                        : result(id, reply.status(), reply.reason(), null);
            }
            case "sr.list" -> {
                StaRemoteProtocol.keys(req, "id", "type");
                var view = currentSr();
                yield result(id, "OK", "PENDING_SR", JSON.toJsonTree(view));
            }
            case "sr.approve" -> {
                StaRemoteProtocol.keys(req, "id", "type", "trainId", "targetNodeId", "graphRevision");
                UUID train = UUID.fromString(StaRemoteProtocol.required(req, "trainId"));
                UUID target = UUID.fromString(StaRemoteProtocol.required(req, "targetNodeId"));
                long revision = req.get("graphRevision").getAsBigDecimal().longValueExact();
                yield approveSr(id, train, target, revision, admin);
            }
            default -> result(id, "REJECTED", "UNKNOWN_OPERATION", null);
        };
    }

    private OperationalAuthorityService.Snapshot currentSr() {
        var services = plugin.getServer().getServicesManager();
        var service = services.load(OperationalAuthorityService.class);
        var network = services.load(RailNetworkService.class);
        if (service == null || network == null) throw new IllegalArgumentException("SERVICE_UNAVAILABLE");
        var view = service.operationalSnapshot();
        long now = System.currentTimeMillis();
        if (view == null || !view.status().equals("AVAILABLE") || view.graphRevision() != network.graphRevision()
                || view.emittedAtMillis() > now || now - view.emittedAtMillis() > 1500)
            throw new IllegalArgumentException("SERVICE_UNAVAILABLE");
        return view;
    }

    private JsonObject approveSr(String requestId, UUID train, UUID target, long revision,
                                 String actor) throws Exception {
        UUID id = UUID.fromString(requestId);
        SrJob job;
        synchronized (this) {
            srJobs.entrySet().removeIf(e -> e.getValue().result().isDone()
                    && System.currentTimeMillis() - e.getValue().at() > 600_000);
            job = srJobs.get(id);
            if (job != null) {
                if (!job.train().equals(train) || !job.target().equals(target)
                        || job.revision() != revision || !job.actor().equals(actor))
                    return result(requestId, "REJECTED", "REQUEST_ID_CONFLICT", null);
            } else {
                if (srJobs.size() >= 256) throw new IllegalArgumentException("BUSY");
                var view = currentSr();
                if (view.graphRevision() != revision) throw new IllegalArgumentException("GRAPH_CHANGED");
                if (view.pendingSr().stream().noneMatch(p -> p.trainId().equals(train)))
                    throw new IllegalArgumentException("NO_PENDING_SR");
                var service = plugin.getServer().getServicesManager().load(OperationalAuthorityService.class);
                CompletableFuture<String> done = new CompletableFuture<>();
                job = new SrJob(train, target, revision, actor, done, System.currentTimeMillis());
                srJobs.put(id, job);
                try {
                    plugin.getServer().getAsyncScheduler().runNow(plugin, task -> {
                        try { done.complete(service.approveSr(train, target, actor)); }
                        catch (RuntimeException ex) { done.completeExceptionally(ex); }
                    });
                } catch (RuntimeException ex) {
                    srJobs.remove(id);
                    throw ex;
                }
            }
        }
        try {
            String reason = job.result().get(30, TimeUnit.SECONDS);
            return result(requestId, reason.equals("APPROVED") ? "APPLIED" : "REJECTED", reason, null);
        } catch (TimeoutException ex) {
            return result(requestId, "UNCONFIRMED", "TIMEOUT", null);
        }
    }

    private static JsonObject result(String id, String status, String reason, JsonElement data) {
        JsonObject response = new JsonObject();
        response.addProperty("protocol", StaRemoteProtocol.VERSION);
        if (id != null) response.addProperty("id", id);
        response.addProperty("status", status);
        response.addProperty("reason", reason);
        if (data != null) response.add("data", data);
        return response;
    }

    private static String readLine(InputStream input) throws IOException {
        var bytes = new java.io.ByteArrayOutputStream();
        while (true) {
            int next = input.read();
            if (next < 0) return bytes.size() == 0 ? null : throwPartial();
            if (next == '\n') return bytes.toString(StandardCharsets.UTF_8);
            if (next == '\r' || bytes.size() >= MAX_REQUEST_BYTES) throw new IOException("Invalid frame");
            bytes.write(next);
        }
    }
    private static String throwPartial() throws IOException { throw new IOException("Incomplete frame"); }

    private static void send(BufferedWriter output, JsonObject message) throws IOException {
        output.write(JSON.toJson(message));
        output.write('\n');
        output.flush();
    }

    @Override public void close() {
        if (!closed.compareAndSet(false, true)) return;
        try { listener.close(); } catch (IOException ignored) { }
        for (var socket : sockets) try { socket.close(); } catch (IOException ignored) { }
        clients.shutdownNow();
    }
}
