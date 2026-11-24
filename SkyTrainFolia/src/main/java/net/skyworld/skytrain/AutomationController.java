package net.skyworld.skytrain;

import java.util.*;
import org.bukkit.entity.Minecart;

/** Optional STA adapter boundary: core STF remains loadable without the API plugin. */
interface AutomationController {
    record ChunkPosition(String world,int x,int z) {}
    boolean owns(Train train);
    List<DriverDeskSnapshot> desks();
    void passenger(Train train,Minecart cart);
    void tick(Train train,long now);
    void moved(Train train,double distance);
    double movementLimit(Train train,double planned);
    String sidebar(Train train);
    List<ChunkPosition> corridor(Train train);
    boolean requestFs(Train train,UUID driver,UUID lease);
    default void retired(UUID train) {}
    default void manualReleased(Train train,UUID driver,UUID lease,long at) {}
    void close();
}
