package synthlax.lostcitiesfork.worldgen.lost;

import synthlax.lostcitiesfork.api.ILostExplosion;
import net.minecraft.core.BlockPos;

public class Explosion implements ILostExplosion {
    private final int radius;
    private final int sqradius;
    private final BlockPos center;

    public Explosion(int radius, BlockPos center) {
        this.radius = radius;
        this.center = center;
        sqradius = radius * radius;
    }

    @Override
    public int getRadius() {
        return radius;
    }

    public int getSqradius() {
        return sqradius;
    }

    @Override
    public BlockPos getCenter() {
        return center;
    }
}
