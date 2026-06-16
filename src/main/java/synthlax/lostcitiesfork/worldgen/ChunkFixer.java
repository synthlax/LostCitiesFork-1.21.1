package synthlax.lostcitiesfork.worldgen;

import synthlax.lostcitiesfork.varia.ChunkCoord;
import synthlax.lostcitiesfork.worldgen.lost.BuildingInfo;
import synthlax.lostcitiesfork.worldgen.lost.cityassets.WorldStyle;
import synthlax.lostcitiesfork.worldgen.lost.regassets.data.WorldSettings;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import javax.annotation.Nullable;

public class ChunkFixer {

    private static void executePostTodo(ChunkCoord coord, IDimensionInfo provider) {
        BuildingInfo info = BuildingInfo.getBuildingInfo(coord, provider);
        info.getPostTodo().forEach((pos, runnable) -> runnable.run());
        info.clearPostTodo();
    }

    @Nullable
    private static ChunkAccess getSafeChunk(LevelAccessor world, int x, int z) {
        if (world.hasChunk(x, z)) {
            try {
                return world.getChunk(x, z, ChunkStatus.FEATURES, false);
            } catch (Throwable t) {
                return null;
            }
        }
        return null;
    }

    private static void generateVines(ChunkCoord coord, LevelAccessor world, IDimensionInfo provider) {
        float vineChance = provider.getProfile().VINE_CHANCE;
        if (vineChance < 0.000001) {
            return;
        }
        int chunkX = coord.chunkX();
        int chunkZ = coord.chunkZ();
        int cx = chunkX << 4;
        int cz = chunkZ << 4;
        BuildingInfo info = BuildingInfo.getBuildingInfo(coord, provider);

        int maxHeight = info.getMaxHeight();

        WorldStyle worldStyle = provider.getWorldStyle();
        WorldSettings worldSettings = worldStyle.getWorldSettings();
        if (info.hasBuilding) {
            ChunkAccess neighbor = getSafeChunk(world, coord.chunkX() + 1, coord.chunkZ());
            if (neighbor != null && neighbor.getPersistedStatus().isOrAfter(ChunkStatus.FEATURES)) {
                BuildingInfo adjacent = info.getXmax();
                int bottom = Math.max(adjacent.getCityGroundLevel() + 3, adjacent.hasBuilding ? adjacent.getMaxHeight() : (adjacent.getCityGroundLevel() + 3));
                BlockState state = worldSettings.vineWest();
                for (int z = 0; z < 15; z++) {
                    for (int y = bottom; y < maxHeight; y++) {
                        if (world.getRandom().nextFloat() < vineChance) {
                            createVineStrip(world, bottom, state, new BlockPos(cx + 16, y, cz + z), new BlockPos(cx + 15, y, cz + z));
                        }
                    }
                }
            }
        }
        if (info.getXmax().hasBuilding) {
            ChunkAccess neighbor = getSafeChunk(world, chunkX + 1, chunkZ);
            if (neighbor != null && neighbor.getPersistedStatus().isOrAfter(ChunkStatus.FEATURES)) {
                BuildingInfo adjacent = info.getXmax();
                int bottom = Math.max(info.getCityGroundLevel() + 3, info.hasBuilding ? maxHeight : (info.getCityGroundLevel() + 3));
                BlockState state = worldSettings.vineEast();
                for (int z = 0; z < 15; z++) {
                    for (int y = bottom; y < (adjacent.getMaxHeight()); y++) {
                        if (world.getRandom().nextFloat() < vineChance) {
                            createVineStrip(world, bottom, state, new BlockPos(cx + 15, y, cz + z), new BlockPos(cx + 16, y, cz + z));
                        }
                    }
                }
            }
        }

        if (info.hasBuilding) {
            ChunkAccess neighbor = getSafeChunk(world, chunkX, chunkZ + 1);
            if (neighbor != null && neighbor.getPersistedStatus().isOrAfter(ChunkStatus.FEATURES)) {
                BuildingInfo adjacent = info.getZmax();
                int bottom = Math.max(adjacent.getCityGroundLevel() + 3, adjacent.hasBuilding ? adjacent.getMaxHeight() : (adjacent.getCityGroundLevel() + 3));
                BlockState state = worldSettings.vineNorth();
                for (int x = 0; x < 15; x++) {
                    for (int y = bottom; y < maxHeight; y++) {
                        if (world.getRandom().nextFloat() < vineChance) {
                            createVineStrip(world, bottom, state, new BlockPos(cx + x, y, cz + 16), new BlockPos(cx + x, y, cz + 15));
                        }
                    }
                }
            }
        }
        if (info.getZmax().hasBuilding) {
            ChunkAccess neighbor = getSafeChunk(world, chunkX, chunkZ + 1);
            if (neighbor != null && neighbor.getPersistedStatus().isOrAfter(ChunkStatus.FEATURES)) {
                BuildingInfo adjacent = info.getZmax();
                int bottom = Math.max(info.getCityGroundLevel() + 3, info.hasBuilding ? maxHeight : (info.getCityGroundLevel() + 3));
                BlockState state = worldSettings.vineSouth();
                for (int x = 0; x < 15; x++) {
                    for (int y = bottom; y < (adjacent.getMaxHeight()); y++) {
                        if (world.getRandom().nextFloat() < vineChance) {
                            createVineStrip(world, bottom, state, new BlockPos(cx + x, y, cz + 15), new BlockPos(cx + x, y, cz + 16));
                        }
                    }
                }
            }
        }
    }

    private static void createVineStrip(LevelAccessor world, int bottom, BlockState state, BlockPos pos, BlockPos vineHolderPos) {
        if (world.isEmptyBlock(vineHolderPos)) {
            return;
        }
        if (!world.isEmptyBlock(pos)) {
            return;
        }
        world.setBlock(pos, state, 0);
        pos = pos.below();
        while (pos.getY() >= bottom && world.getRandom().nextFloat() < .8f) {
            if (!world.isEmptyBlock(pos)) {
                return;
            }
            world.setBlock(pos, state, 0);
            pos = pos.below();
        }
    }

    public static void fix(IDimensionInfo info, ChunkCoord coord) {
        generateVines(coord, info.getWorld(), info);
        executePostTodo(coord, info);
    }
}