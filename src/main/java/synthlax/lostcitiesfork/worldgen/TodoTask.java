package synthlax.lostcitiesfork.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

public interface TodoTask {
    BlockPos pos();
    void execute(ServerLevel level);
}
