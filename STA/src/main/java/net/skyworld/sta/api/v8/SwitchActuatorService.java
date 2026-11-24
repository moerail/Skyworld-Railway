package net.skyworld.sta.api.v8;

import java.util.concurrent.CompletionStage;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import net.skyworld.sta.api.v5.SwitchControlService;

/** STF physical endpoint. STCS supplies and rechecks the occupancy/route-lock guard before execution. */
public interface SwitchActuatorService {
    CompletionStage<String> change(SwitchControlService.Request request,BooleanSupplier guard,Consumer<String> progress);
    default CompletionStage<String> change(AtoProtocol.Header h,SwitchControlService.Request request,BooleanSupplier guard,Consumer<String> progress) {
        h.require(AtoProtocol.Packet.SWITCH_CHANGE);return change(request,guard,progress);
    }
}
