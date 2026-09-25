package net.skyworld.skypcc;
import net.skyworld.suite.SuiteCommandUi;

import com.google.gson.*;
import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import org.bukkit.plugin.java.JavaPlugin;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.skyworld.sta.api.v5.*;
import net.skyworld.sta.api.v6.OperationalAuthorityService;
import net.skyworld.sta.api.v3.RailwayEventService;
import net.skyworld.sta.api.v3.ConsistObservationService;
import net.skyworld.sta.api.v5.*;

/** Server source reconstructed for M0; the 0.2.2 web assets are preserved and adapted.
 * All HTTP handlers read cached bytes. No entity/region access or provider calls on HTTP threads. */
public final class SkyPccPlugin extends JavaPlugin {
    @Override public boolean onCommand(org.bukkit.command.CommandSender sender, org.bukkit.command.Command command, String label, String[] args) {
        if (!SuiteCommandUi.handle(this, sender, "skypcc", args))
            SuiteCommandUi.handle(this, sender, "skypcc", new String[]{"help"});
        return true;
    }
    @Override public java.util.List<String> onTabComplete(org.bukkit.command.CommandSender sender, org.bukkit.command.Command command, String alias, String[] args) {
        if (args.length == 1) return java.util.List.of("help", "version").stream()
                .filter(s -> s.startsWith(args[0].toLowerCase(java.util.Locale.ROOT))).toList();
        return SuiteCommandUi.complete("skypcc", args);
    }

    private static final Gson JSON = new GsonBuilder().serializeNulls().create();
    private HttpServer server;
    private ExecutorService executor;
    private ScheduledTask refreshTask;
    private volatile byte[] graph = bytes("{}"), snapshot = bytes("{}"), messages = bytes("{}");
    private volatile byte[] operationalEvents = bytes("{\"status\":\"UNAVAILABLE\",\"events\":[]}");
    private RailwayEventService.History eventHistory;
    private volatile boolean running;
    private final Semaphore streamSlots = new Semaphore(8);
    private final Object update = new Object();
    private final Map<String, byte[]> assets = new HashMap<>();
    private int interval;
    private String mode;
    private volatile long generation;
    private volatile byte[] shadowMa = bytes("{\"status\":\"UNAVAILABLE\"}");
    private volatile byte[] operationalMa = bytes("{\"status\":\"UNAVAILABLE\"}");
    private volatile OperationalAuthorityService operationalSource;
    private volatile long operationalRevision = -1;
    private volatile Set<UUID> pendingSrTrains = Set.of();
    private final SrGateway srGateway = new SrGateway();
    private volatile SwitchControlService switchControl;
    private final SwitchGateway switchGateway = new SwitchGateway();
    private boolean controlEnabled;
    private String controlToken;
    @Override public void onEnable() {
        saveDefaultConfig();
        if (!getConfig().getBoolean("web.enabled", true)) return;
        interval = Math.max(100, getConfig().getInt("web.poll-interval-millis", 1000));
        mode = getConfig().getString("web.update-mode", "auto");
        controlToken = getConfig().getString("web.control-token", "");
        controlEnabled = getConfig().getBoolean("web.control-enabled", false) && controlToken.length() >= 32;
        if (getConfig().getBoolean("web.control-enabled", false) && !controlEnabled)
            getLogger().warning("PCC control disabled: configure a private token of at least 32 characters.");
        if (!Set.of("auto","sse","poll").contains(mode)) mode = "auto";
        try {
            for (String path : List.of("index.html", "app.js", "i18n.js", "styles.css", "day_logo.png", "night_logo.png")) {
                try (InputStream in = getResource("web/" + path)) {
                    if (in == null) throw new IOException("Missing asset " + path);
                    assets.put(path, in.readAllBytes());
                }
            }
            server = HttpServer.create(new InetSocketAddress(getConfig().getString("web.bind-address", "127.0.0.1"),
                    getConfig().getInt("web.port", 8765)), 32);
            executor = new ThreadPoolExecutor(12, 12, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(64), r -> {
                Thread t = new Thread(r, "SkyPCC-http"); t.setDaemon(true); return t;
            }, new ThreadPoolExecutor.AbortPolicy());
            server.setExecutor(executor); server.createContext("/", this::handle);
            running = true; refresh();
            refreshTask = getServer().getAsyncScheduler().runAtFixedRate(this, t -> refresh(), 100, 250, TimeUnit.MILLISECONDS);
            server.start(); getLogger().info("SkyPCC STA v5 map listening on " + server.getAddress());
        } catch (IOException | RuntimeException ex) {
            onDisable(); getLogger().severe("SkyPCC could not start: " + ex);
            getServer().getPluginManager().disablePlugin(this);
        }
    }
    private synchronized void refresh() {
        long now = System.currentTimeMillis();
        var svc = getServer().getServicesManager();
        var tracking = svc.load(TrackingService.class); var network = svc.load(RailNetworkService.class);
        JsonObject report = new JsonObject();
        report.addProperty("protocol", "STA"); report.addProperty("M_VERSION", StaMessage.VERSION); report.addProperty("serverTimeMillis", now);
        JsonArray encoded = new JsonArray(); List<Map<String,Object>> trains = new ArrayList<>();
        String status = "SERVICE_UNAVAILABLE"; long revision = 0;
        try {
            if (tracking != null && network != null && tracking.protocolVersion() == net.skyworld.sta.api.v5.StaMessage.VERSION && network.protocolVersion() == net.skyworld.sta.api.v5.StaMessage.VERSION) {
                revision = network.graphRevision(); graph = bytes(network.graphJson());
                status = tracking.sourceAvailable() ? "AVAILABLE" : "SOURCE_UNAVAILABLE";
                for (StaMessage m : tracking.snapshots()) {
                    encoded.add(StaJson.tree(m));
                    if (m.header().kind() == StaMessage.Kind.TRACK_REPORT) {
                        Map<String,Object> train = new LinkedHashMap<>(PccProjection.train(m, now));
                        if (!tracking.sourceAvailable() || now - m.header().emittedAtMillis() >= 2000
                                || m.tracking().graphRevision() != revision) {
                            train.put("stale", true); train.put("graphCurrent", false);
                            train.put("quality", !tracking.sourceAvailable() ? "SOURCE_UNAVAILABLE"
                                    : m.tracking().graphRevision() != revision ? "GRAPH_CHANGED" : "STALE");
                        }
                        trains.add(train);
                    }
                }
            } else graph = bytes("{}");
        } catch (RuntimeException ex) { status = "SERVICE_UNAVAILABLE"; trains.clear(); encoded = new JsonArray(); }
        // The restored STF roster is available before any player loads train chunks.
        // Keep this independent of the STCS tracking/network startup order.
        try {
            var roster = svc.load(ConsistObservationService.class);
            if (roster != null && roster.consistProtocolVersion() == 3) {
                trains = PccTrainRoster.merge(trains, roster.consistObservations());
            }
        } catch (RuntimeException | LinkageError ex) { /* Tracking rows remain usable without roster support. */ }
        report.addProperty("serviceStatus", status); report.addProperty("graphRevision", revision); report.add("messages", encoded);
        messages = bytes(JSON.toJson(report));
        String eventStatus = "UNAVAILABLE";
        try {
            var eventService = svc.load(RailwayEventService.class);
            if (eventService != null) { eventHistory = eventService.history(); eventStatus = "AVAILABLE"; }
        } catch (RuntimeException | LinkageError ex) { eventStatus = "UNAVAILABLE"; }
        Map<String, Object> eventReport = new LinkedHashMap<>();
        eventReport.put("status", eventStatus);
        eventReport.put("session", eventHistory == null ? "" : eventHistory.session().toString());
        eventReport.put("latestSequence", eventHistory == null ? 0 : eventHistory.latestSequence());
        eventReport.put("evictedCount", eventHistory == null ? 0 : eventHistory.evictedCount());
        eventReport.put("events", eventHistory == null ? List.of() : eventHistory.events());
        operationalEvents = bytes(JSON.toJson(eventReport));
        Object ma = Map.of("status", "UNAVAILABLE");
        try {
            var source = svc.load(ShadowAuthorityService.class);
            if (source != null) ma = source.snapshot();
            switchControl = svc.load(SwitchControlService.class);
        } catch (RuntimeException | LinkageError ex) { switchControl = null; }
        shadowMa = bytes(JSON.toJson(ma));
        Object activeMa = Map.of("status", "UNAVAILABLE");
        try {
            var source = svc.load(OperationalAuthorityService.class);
            var view = source == null ? null : source.operationalSnapshot();
            if (view != null && view.status().equals("AVAILABLE") && view.graphRevision() == revision
                    && now >= view.emittedAtMillis() && now - view.emittedAtMillis() <= 1500) {
                activeMa = view;
                operationalSource = source;
                operationalRevision = view.graphRevision();
                pendingSrTrains = view.pendingSr().stream().map(OperationalAuthorityService.PendingSr::trainId)
                        .collect(java.util.stream.Collectors.toUnmodifiableSet());
            } else {
                operationalSource = null; operationalRevision = -1; pendingSrTrains = Set.of();
            }
        } catch (RuntimeException | LinkageError ex) {
            operationalSource = null; operationalRevision = -1; pendingSrTrains = Set.of();
        }
        operationalMa = bytes(JSON.toJson(activeMa));
        snapshot = bytes(JSON.toJson(Map.of("schemaVersion", 5, "serverTimeMillis", now, "graphRevision", revision,
                "serviceStatus", status, "trains", trains, "operationalEvents", eventReport,
                "shadowMa", ma, "operationalMa", activeMa)));
        synchronized (update) { generation++; update.notifyAll(); }
    }
    private void handle(HttpExchange x) throws IOException {
        if ("/api/v5/switch".equals(x.getRequestURI().getPath())) { control(x); return; }
        if ("/api/v6/sr".equals(x.getRequestURI().getPath())) { srControl(x); return; }
        if (!"GET".equals(x.getRequestMethod())) { reply(x, 405, "text/plain", bytes("GET required")); return; }
        String path = x.getRequestURI().getPath();
        switch (path) {
            case "/api/v5/graph" -> reply(x, 200, "application/json", graph);
            case "/api/v5/trains" -> reply(x, 200, "application/json", snapshot);
            case "/api/v5/messages" -> reply(x, 200, "application/json", messages);
            case "/api/v5/railway-events" -> reply(x, 200, "application/json", operationalEvents);
            case "/api/v5/shadow-ma" -> reply(x, 200, "application/json", shadowMa);
            case "/api/v6/operational-ma" -> reply(x, 200, "application/json", operationalMa);
            case "/api/v5/config" -> reply(x, 200, "application/json", bytes(JSON.toJson(Map.of("updateMode", mode, "pollIntervalMillis", interval, "controlEnabled", controlEnabled))));
            case "/api/v5/events" -> stream(x);
            case "/", "/index.html" -> reply(x, 200, "text/html", assets.get("index.html"));
            case "/assets/app.js" -> reply(x, 200, "text/javascript", assets.get("app.js"));
            case "/assets/i18n.js" -> reply(x, 200, "text/javascript", assets.get("i18n.js"));
            case "/assets/styles.css" -> reply(x, 200, "text/css", assets.get("styles.css"));
            case "/assets/day_logo.png" -> reply(x, 200, "image/png", assets.get("day_logo.png"));
            case "/assets/night_logo.png" -> reply(x, 200, "image/png", assets.get("night_logo.png"));
            default -> reply(x, 404, "text/plain", bytes("Not found"));
        }
    }
    private void control(HttpExchange x) throws IOException {
        var h = x.getRequestHeaders();
        if (!ControlAccess.permitted(controlEnabled, controlToken, server.getAddress().getPort(), x.getRemoteAddress().getAddress(),
                x.getRequestMethod(), h.getFirst("Host"), h.getFirst("Origin"), h.getFirst("Authorization"), h.getFirst("Content-Type"))) {
            reply(x, 403, "application/json", bytes("{\"status\":\"REJECTED\",\"reason\":\"LOCAL_AUTH_REQUIRED\"}")); return;
        }
        byte[] body = x.getRequestBody().readNBytes(4097);
        if (body.length > 4096) { reply(x, 413, "application/json", bytes("{}")); return; }
        try {
            JsonObject o = JsonParser.parseString(new String(body, StandardCharsets.UTF_8)).getAsJsonObject();
            if (!o.keySet().equals(Set.of("requestId", "switchId", "graphRevision", "expectedState", "targetState", "position"))) throw new IllegalArgumentException();
            var p=o.getAsJsonObject("position");
            if(!p.keySet().equals(Set.of("world","x","y","z")))throw new IllegalArgumentException();
            var request = new SwitchControlService.Request(UUID.fromString(o.get("requestId").getAsString()),
                    UUID.fromString(o.get("switchId").getAsString()), o.get("graphRevision").getAsBigDecimal().longValueExact(),
                    o.get("expectedState").getAsString(), o.get("targetState").getAsString(),
                    new SwitchControlService.Position(p.get("world").getAsString(),p.get("x").getAsBigDecimal().intValueExact(),
                            p.get("y").getAsBigDecimal().intValueExact(),p.get("z").getAsBigDecimal().intValueExact()));
            var future = switchGateway.submit(request, switchControl,
                    work -> getServer().getAsyncScheduler().runNow(this, task -> work.run()), System.currentTimeMillis());
            var result = future.getNow(null);
            if(result==null)result=switchGateway.progress(request,switchControl);
            reply(x, result.status().equals("PENDING") ? 202 : 200, "application/json", bytes(JSON.toJson(result)));
        } catch (RuntimeException ex) { reply(x, 400, "application/json", bytes("{\"status\":\"REJECTED\",\"reason\":\"INVALID_REQUEST\"}")); }
    }
    private void srControl(HttpExchange x) throws IOException {
        var h = x.getRequestHeaders();
        if (!ControlAccess.permitted(controlEnabled, controlToken, server.getAddress().getPort(),
                x.getRemoteAddress().getAddress(), x.getRequestMethod(), h.getFirst("Host"),
                h.getFirst("Origin"), h.getFirst("Authorization"), h.getFirst("Content-Type"))) {
            reply(x, 403, "application/json", bytes("{\"status\":\"REJECTED\",\"reason\":\"LOCAL_AUTH_REQUIRED\"}"));
            return;
        }
        byte[] body = x.getRequestBody().readNBytes(4097);
        if (body.length > 4096) { reply(x, 413, "application/json", bytes("{}")); return; }
        try {
            JsonObject o = JsonParser.parseString(new String(body, StandardCharsets.UTF_8)).getAsJsonObject();
            if (!o.keySet().equals(Set.of("requestId", "trainId", "targetNodeId", "graphRevision")))
                throw new IllegalArgumentException("Invalid SR request");
            var request = new SrGateway.Request(UUID.fromString(o.get("requestId").getAsString()),
                    UUID.fromString(o.get("trainId").getAsString()),
                    UUID.fromString(o.get("targetNodeId").getAsString()),
                    o.get("graphRevision").getAsBigDecimal().longValueExact());
            var result = srGateway.submit(request, operationalRevision, pendingSrTrains,
                    operationalSource, work -> getServer().getAsyncScheduler().runNow(this, task -> work.run()),
                    System.currentTimeMillis());
            reply(x, result.status().equals("PENDING") ? 202 : 200, "application/json", bytes(JSON.toJson(result)));
        } catch (RuntimeException ex) {
            reply(x, 400, "application/json", bytes("{\"status\":\"REJECTED\",\"reason\":\"INVALID_REQUEST\"}"));
        }
    }
    private void stream(HttpExchange x) throws IOException {
        if (!streamSlots.tryAcquire()) { reply(x, 503, "text/plain", bytes("Stream limit reached; use polling")); return; }
        try {
            x.getResponseHeaders().set("Content-Type", "text/event-stream; charset=utf-8");
            x.getResponseHeaders().set("Cache-Control", "no-store"); x.getResponseHeaders().set("X-Accel-Buffering", "no");
            x.sendResponseHeaders(200, 0);
            try (var out = x.getResponseBody()) {
                long sent = -1;
                while (running && !Thread.currentThread().isInterrupted()) {
                    synchronized (update) {
                        if (sent == generation) update.wait(15000);
                        sent = generation;
                    }
                    out.write(bytes("event: snapshot\ndata: ")); out.write(snapshot); out.write(bytes("\n\n")); out.flush();
                }
            }
        } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
        finally { x.close(); streamSlots.release(); }
    }
    private static byte[] bytes(String s) { return s.getBytes(StandardCharsets.UTF_8); }
    private static void reply(HttpExchange x, int status, String type, byte[] body) throws IOException {
        try {
            x.getResponseHeaders().set("Content-Type", type.startsWith("image/") ? type : type + "; charset=utf-8");
            x.getResponseHeaders().set("Cache-Control", "no-store"); x.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
            x.sendResponseHeaders(status, body.length); x.getResponseBody().write(body);
        } finally { x.close(); }
    }
    @Override public synchronized void onDisable() {
        running = false;
        if (refreshTask != null) refreshTask.cancel();
        synchronized (update) { update.notifyAll(); }
        if (server != null) server.stop(0);
        if (executor != null) executor.shutdownNow();
    }
}
