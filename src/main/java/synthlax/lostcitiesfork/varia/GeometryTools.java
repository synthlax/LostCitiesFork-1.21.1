package synthlax.lostcitiesfork.varia;

import net.minecraft.world.phys.AABB;
import net.minecraft.core.BlockPos;

public class GeometryTools {

    public static double squaredDistanceBoxPoint(AABB chunkBox, BlockPos center) {
        double dmin = 0;

        if (center.getX() < chunkBox.minX) {
            double dx = center.getX() - chunkBox.minX;
            dmin += dx * dx;
        } else if (center.getX() > chunkBox.maxX) {
            double dx = center.getX() - chunkBox.maxX;
            dmin += dx * dx;
        }

        if (center.getY() < chunkBox.minY) {
            double dy = center.getY() - chunkBox.minY;
            dmin += dy * dy;
        } else if (center.getY() > chunkBox.maxY) {
            double dy = center.getY() - chunkBox.maxY;
            dmin += dy * dy;
        }

        if (center.getZ() < chunkBox.minZ) {
            double dz = center.getZ() - chunkBox.minZ;
            dmin += dz * dz;
        } else if (center.getZ() > chunkBox.maxZ) {
            double dz = center.getZ() - chunkBox.maxZ;
            dmin += dz * dz;
        }
        return dmin;
    }

    public static double maxSquaredDistanceBoxPoint(AABB chunkBox, BlockPos center) {
        double dmax = 0;

        if (center.getX() < (chunkBox.minX + chunkBox.maxX) / 2) {
            double dx = center.getX() - chunkBox.maxX;
            dmax += dx * dx;
        } else {
            double dx = center.getX() - chunkBox.minX;
            dmax += dx * dx;
        }

        if (center.getY() < (chunkBox.minY + chunkBox.maxY) / 2) {
            double dy = center.getY() - chunkBox.maxY;
            dmax += dy * dy;
        } else {
            double dy = center.getY() - chunkBox.minY;
            dmax += dy * dy;
        }

        if (center.getZ() < (chunkBox.minZ + chunkBox.maxZ) / 2) {
            double dz = center.getZ() - chunkBox.maxZ;
            dmax += dz * dz;
        } else {
            double dz = center.getZ() - chunkBox.minZ;
            dmax += dz * dz;
        }
        return dmax;
    }

    public static double squaredDistanceBoxPoint(AxisAlignedBB2D chunkBox, int x, int y) {
        double dmin = 0;

        if (x < chunkBox.minX) {
            double dx = x - chunkBox.minX;
            dmin += dx * dx;
        } else if (x > chunkBox.maxX) {
            double dx = x - chunkBox.maxX;
            dmin += dx * dx;
        }

        if (y < chunkBox.minY) {
            double dy = y - chunkBox.minY;
            dmin += dy * dy;
        } else if (y > chunkBox.maxY) {
            double dy = y - chunkBox.maxY;
            dmin += dy * dy;
        }
        return dmin;
    }

    public static class AxisAlignedBB2D {
        public final double minX;
        public final double minY;
        public final double maxX;
        public final double maxY;
        public int height;

        public AxisAlignedBB2D(double minX, double minY, double maxX, double maxY) {
            this.minX = minX;
            this.minY = minY;
            this.maxX = maxX;
            this.maxY = maxY;
        }

        public double getMinX() {
            return minX;
        }

        public double getMinY() {
            return minY;
        }

        public double getMaxX() {
            return maxX;
        }

        public double getMaxY() {
            return maxY;
        }
    }
}