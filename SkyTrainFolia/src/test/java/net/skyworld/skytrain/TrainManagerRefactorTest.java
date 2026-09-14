package net.skyworld.skytrain;

import java.io.InputStreamReader;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Minecart;

public final class TrainManagerRefactorTest {
    public static void main(String[] args) throws Exception {
        VehicleProfile profile;
        try (var reader = new InputStreamReader(TrainManagerRefactorTest.class.getResourceAsStream(
                "/vehicles/minecraft-comfort.yml"), StandardCharsets.UTF_8)) {
            profile = VehicleProfile.parse("minecraft-comfort", YamlConfiguration.loadConfiguration(reader));
        }
        var config = new AtomicReference<>(new YamlConfiguration());
        TrainSettings settings = new TrainSettings(config::get, () -> profile, () -> 1.0);
        near(settings.defaultSpeed(), .35);
        near(settings.maxAllowedSpeed(), Math.min(profile.maxSpeed(), 1.0));
        assert settings.trainPhysicsHardLock();
        var replacement = new YamlConfiguration();
        replacement.set("settings.default-speed", -1);
        replacement.set("settings.track-coordinate-physics", false);
        replacement.set("settings.trackside-running-sound-min-pitch", 7);
        config.set(replacement);
        near(settings.defaultSpeed(), 0);
        near(settings.tracksideRunningSoundMinPitch(), 2);
        near(settings.tracksideRunningSoundMaxPitch(), 2);
        assert !settings.trainPhysicsHardLock() : "Settings must follow the reloaded configuration";
        config.set(new YamlConfiguration());
        persistence(settings);
        controls(settings);
        relocationAndTickGate();
        System.out.println("PASS extracted settings reload, YAML roundtrip/recovery, control gates and pending relocation");
    }

    private static void persistence(TrainSettings settings) throws Exception {
        Path folder = Files.createTempDirectory(Path.of("."), "train-persistence-");
        Map<UUID, Train> trains = new ConcurrentHashMap<>();
        Map<String, UUID> names = new ConcurrentHashMap<>();
        Map<UUID, UUID> carts = new ConcurrentHashMap<>();
        Map<String, SavedTrainDefinition> templates = new ConcurrentHashMap<>();
        Logger logger = Logger.getAnonymousLogger();
        logger.setLevel(Level.OFF);
        TrainPersistence store = new TrainPersistence(folder.toFile(), logger, settings,
                trains, names, carts, templates);
        for (ProtectionMode mode : ProtectionMode.values()) {
            Train train = new Train(UUID.randomUUID(), mode.name(), .4, .9, 1.1);
            train.protectionMode = mode;
            train.addMember(UUID.randomUUID());
            train.addMember(UUID.randomUUID());
            train.properties().setTrainNumber("0012");
            train.loadMileage("test_down", true, 123.5, -1,
                    new org.bukkit.util.Vector(-1, 0, 0), "0018", 12, true);
            trains.put(train.id(), train);
        }
        Train manual = trains.values().iterator().next();
        manual.manualTakeover = true;
        manual.manualReleaseConfirmed = false;
        manual.powerNotch = 4;
        manual.lastManualDriver = UUID.randomUUID();
        UUID manualId = manual.id();
        templates.put("template", SavedTrainDefinition.fromTrain("template", manual));
        store.save();
        assert !Files.exists(folder.resolve("trains.yml.tmp"));
        String validTrains = Files.readString(folder.resolve("trains.yml"));
        trains.clear();
        templates.clear();
        store.load();
        assert trains.size() == 4 && names.size() == 4 && carts.size() == 8;
        assert templates.get("template").memberCount == 2;
        for (Train train : trains.values()) {
            assert train.protectionMode.name().equals(train.name());
            assert train.properties().trainNumber.equals("0012");
            assert names.get(train.key()).equals(train.id());
            assert train.members().stream().allMatch(id -> train.id().equals(carts.get(id)));
            assert train.mileagePersistence().lineName().equals("test_down");
            near(train.mileagePersistence().meters(), 123.5);
        }
        Train restoredManual = trains.get(manualId);
        assert restoredManual.emergencyBrake && restoredManual.powerNotch == 0;
        assert restoredManual.driverEmergencyHold && !restoredManual.manualReleaseConfirmed;
        assert restoredManual.lastManualDriver.equals(manual.lastManualDriver);
        Files.writeString(folder.resolve("trains.yml"), "trains: [unterminated");
        store.load();
        assert trains.get(manualId) == restoredManual : "Unreadable data must not replace the live registry";
        try (var files = Files.list(folder)) {
            assert files.anyMatch(p -> p.getFileName().toString().startsWith("trains.yml.corrupt-"));
        }
        Files.writeString(folder.resolve("trains.yml"), validTrains);
        SavedTrainDefinition retained = templates.get("template");
        Files.writeString(folder.resolve("savedtrains.yml"), "saved-trains: [unterminated");
        store.load();
        assert templates.get("template") == retained : "Unreadable templates keep the previous definitions";
    }

    private static void controls(TrainSettings settings) {
        Train train = new Train(UUID.randomUUID(), "controls", .4, 1, 1.1);
        AtomicInteger saves = new AtomicInteger();
        TrainDrivingControls controls = new TrainDrivingControls(settings, new Object(), name -> train,
                saves::incrementAndGet, ignored -> { });
        train.protectionMode = ProtectionMode.RECOVERING;
        rejects(() -> controls.setPowerNotch(train, 4));
        assert saves.get() == 0 && train.powerNotch == 0;
        train.protectionMode = ProtectionMode.SHADOW;
        train.driverEmergencyHold = true;
        rejects(() -> controls.setPowerNotch(train, 4));
        train.driverEmergencyHold = false;
        train.automaticRun = new AutomaticRun("station",
                AutomaticSignSpec.parse("station", "5", "continue", .4), 100, false, false);
        controls.setPowerNotch(train, 4);
        assert train.powerNotch == 4 && train.brakeNotch == 0 && train.manualTakeover;
        assert !train.manualReleaseConfirmed && saves.get() == 1 && train.automaticRun == null;
        controls.setBrakeNotch(train, 7);
        assert train.powerNotch == 0 && train.brakeNotch == 7;
        controls.emergencyBrake(train);
        assert train.emergencyBrake;
        controls.confirmManualRelease(train);
        TrainDrivingControls.authorizeAutomatic(train, false, .001);
        assert !train.emergencyBrake && !train.manualTakeover;
    }

    private static void relocationAndTickGate() {
        UUID id = UUID.randomUUID();
        AtomicInteger sync = new AtomicInteger(), async = new AtomicInteger(), validations = new AtomicInteger();
        var future = new AtomicReference<>(new CompletableFuture<Boolean>());
        Minecart cart = (Minecart) Proxy.newProxyInstance(Minecart.class.getClassLoader(),
                new Class<?>[] {Minecart.class}, (self, method, args) -> switch (method.getName()) {
                    case "getUniqueId" -> id;
                    case "teleport" -> { sync.incrementAndGet(); yield false; }
                    case "teleportAsync" -> { async.incrementAndGet(); yield future.get(); }
                    default -> throw new AssertionError("Unexpected entity access: " + method.getName());
                });
        TrainMemberActuator actuator = new TrainMemberActuator(null, null);
        assert !actuator.teleportTrainMember(cart, new Location(null, 1, 2, 3), false);
        assert actuator.relocationPending(id);
        assert !actuator.teleportTrainMember(cart, new Location(null, 4, 5, 6), false);
        assert sync.get() == 1 && async.get() == 1 : "Do not issue a second move while one is pending";
        TrainMotionController.Host host = (TrainMotionController.Host) Proxy.newProxyInstance(
                TrainMotionController.Host.class.getClassLoader(), new Class<?>[] {TrainMotionController.Host.class},
                (self, method, args) -> {
                    if (!method.getName().equals("validateMemberTick")) throw new AssertionError(method.getName());
                    validations.incrementAndGet();
                    return true;
                });
        TrainMotionController motion = new TrainMotionController(null, null, host, null, null,
                null, null, null, actuator, null);
        motion.tickMember(null, cart, null);
        assert validations.get() == 1 : "Lifecycle validation still precedes the relocation gate";
        future.get().complete(true);
        assert !actuator.relocationPending(id);
        future.set(new CompletableFuture<>());
        assert !actuator.teleportTrainMember(cart, new Location(null, 7, 8, 9), false);
        assert async.get() == 2;
        future.get().complete(false);
        assert !actuator.relocationPending(id) : "A failed move also releases its pending reservation";
    }

    private static void rejects(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("Expected rejection");
    }

    private static void near(double actual, double expected) {
        assert Math.abs(actual - expected) < 1e-9 : actual + " != " + expected;
    }
}
