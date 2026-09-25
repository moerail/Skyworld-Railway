package net.skyworld.skytrain;

import java.lang.classfile.ClassFile;
import java.lang.classfile.instruction.InvokeInstruction;
import java.util.LinkedHashMap;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.block.data.Rail;
import org.bukkit.util.Vector;

public final class TrainReloadGeometryTest {
    public static void main(String[] args) throws Exception {
        // Guard the production entry point against reintroducing cold-start teardown on reload.
        try (var stream = TrainManager.class.getResourceAsStream("TrainManager.class")) {
            var model = ClassFile.of().parse(stream.readAllBytes());
            var reload = model.methods().stream().filter(m -> m.methodName().equalsString("reloadAll"))
                    .findFirst().orElseThrow();
            boolean revokes = false, saves = false;
            for (var element : reload.code().orElseThrow()) {
                if (!(element instanceof InvokeInstruction call)) continue;
                String owner = call.owner().asInternalName(), name = call.name().stringValue();
                assert !(owner.endsWith("/TrainManager") && name.equals("shutdown"));
                assert !(owner.endsWith("/TrainPersistence") && name.equals("load"));
                assert !owner.endsWith("/TrainDisplaySync") : "Reload must not reset live display ownership";
                if (owner.endsWith("/DriverControlService") && name.equals("shutdown")) revokes = true;
                if (owner.endsWith("/TrainPersistence") && name.equals("save")) saves = true;
            }
            assert revokes && saves;
        }
        RailSignAccessTest.main(args);
        RailSignAccessTest.owned = true;
        RailSignAccessTest.loaded = true;
        UUID worldId = UUID.randomUUID();
        RailSignAccessTest.world = RailSignAccessTest.proxy(org.bukkit.World.class, (method, values) -> switch (method) {
            case "getName" -> "reload-world";
            case "getUID" -> worldId;
            case "getMinHeight" -> -64;
            case "getMaxHeight" -> 320;
            case "isChunkLoaded" -> true;
            case "getBlockAt" -> values[0] instanceof Location location
                    ? RailSignAccessTest.block(location.getBlockX(), location.getBlockY(), location.getBlockZ())
                    : RailSignAccessTest.block((int) values[0], (int) values[1], (int) values[2]);
            default -> null;
        });
        RailSignAccessTest.data.clear();
        for (int z = -20; z <= 20; z++) {
            Rail rail = RailSignAccessTest.proxy(Rail.class,
                    (method, values) -> method.equals("getShape") ? Rail.Shape.NORTH_SOUTH : null);
            RailSignAccessTest.data.put(RailSignAccessTest.key(0, 64, z), rail);
        }
        for (boolean reversed : new boolean[] {false, true}) {
            var train = new Train(UUID.randomUUID(), "reload", .4, 1, 1.1);
            train.reversed = reversed;
            train.reverser = reversed ? Reverser.BACKWARD : Reverser.FORWARD;
            Vector direction = new Vector(0, 0, reversed ? -1 : 1);
            train.rememberDirection(direction);
            for (int i = 0; i < 6; i++) train.addMember(UUID.randomUUID());
            var members = train.members();
            var path = TrainRailPath.create(new Location(RailSignAccessTest.world, .5, 64.1, .5),
                    direction, reversed, train.spacing * 5);
            assert path != null;
            train.trackPath(path);
            var placements = path.placements(6, train.spacing, reversed);
            var targets = new LinkedHashMap<UUID, TrainMemberTarget>();
            for (int i = 0; i < 6; i++) {
                var placement = placements.get(i);
                targets.put(members.get(i), new TrainMemberTarget(placement.location(), placement.direction(), 0, 1000));
                train.snapshot(members.get(i), new MemberSnapshot(members.get(i), placement.location(), new Vector(), 1000));
            }
            train.publishMotionFrame(targets, 1000, 20);
            for (int reload = 0; reload < 3; reload++) {
                DriverSafety.brake(train);
                assert train.trackPath() == path && train.members().equals(members);
                assert train.reversed == reversed && train.rememberedDirection().equals(direction);
                for (UUID member : members) {
                    assert train.memberTarget(member, 21) == targets.get(member);
                    assert train.snapshot(member).toVector().distanceSquared(targets.get(member).location.toVector()) < 1e-12;
                }
            }
        }
        System.out.println("PASS reload lifecycle guard and six-member forward/reverse geometry, frame and direction retention across repeated driver revocation");
    }
}
