package synthlax.lostcitiesfork.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.state.BlockState;

public record SaplingTodoTask(BlockPos pos, BlockState state) implements TodoTask {
    @Override
    public void execute(ServerLevel level) {
        if (level.isAreaLoaded(pos, 1) && level.getBlockState(pos).getBlock() instanceof SaplingBlock saplingBlock) {
            level.setBlock(pos, state, Block.UPDATE_CLIENTS);
            saplingBlock.advanceTree(level, pos, state, level.getRandom());
        }
    }
}
