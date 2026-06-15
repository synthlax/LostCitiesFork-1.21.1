package synthlax.lostcitiesfork.worldgen.lost;

import synthlax.lostcitiesfork.config.LostCityProfile;
import synthlax.lostcitiesfork.varia.ChunkCoord;
import synthlax.lostcitiesfork.varia.PerlinNoiseGenerator14;
import synthlax.lostcitiesfork.worldgen.IDimensionInfo;

import java.util.function.Function;

import synthlax.lostcitiesfork.varia.TimedCache;
import synthlax.lostcitiesfork.setup.Config;

public class Highway {

    private static volatile PerlinNoiseGenerator14 perlinX = null;
    private static volatile PerlinNoiseGenerator14 perlinZ = null;
    private static final TimedCache<ChunkCoord, Integer> X_HIGHWAY_LEVEL_CACHE = new TimedCache<>("X_HIGHWAY_LEVEL_CACHE", Config.CACHE_CLEANUP_SECONDS::get);
    private static final TimedCache<ChunkCoord, Integer> Z_HIGHWAY_LEVEL_CACHE = new TimedCache<>("Z_HIGHWAY_LEVEL_CACHE", Config.CACHE_CLEANUP_SECONDS::get);


    private static synchronized void makePerlin(long seed) {
        if (perlinX == null) {
            perlinX = new PerlinNoiseGenerator14(seed, 4);
        }
        if (perlinZ == null) {
            perlinZ = new PerlinNoiseGenerator14(seed, 4);
        }
    }

    public static synchronized void cleanCache() {
        perlinX = null;
        perlinZ = null;
        X_HIGHWAY_LEVEL_CACHE.clear();
        Z_HIGHWAY_LEVEL_CACHE.clear();
    }

    public static boolean hasHighway(ChunkCoord coord, IDimensionInfo provider, LostCityProfile profile) {
        if (getXHighwayLevel(coord, provider, profile) >= 0) {
            return true;
        }
        if (getZHighwayLevel(coord, provider, profile) >= 0) {
            return true;
        }
        return false;
    }

    /**
     * Returns -1 if there is no highway in X direction that goes through this chunk.
     * Returns 0 or 1 if there is a highway (at that city level) going through this chunk.
     */
    public static int getXHighwayLevel(ChunkCoord coord, IDimensionInfo provider, LostCityProfile profile) {
        return getHighwayLevel(provider, profile, Highway.X_HIGHWAY_LEVEL_CACHE, cp -> hasXHighway(cp, profile), Orientation.X, coord);
    }

    /**
     * Returns -1 if there is no highway in Z direction that goes through this chunk.
     * Returns 0 or 1 if there is a highway (at that city level) going through this chunk.
     */
    public static int getZHighwayLevel(ChunkCoord coord, IDimensionInfo provider, LostCityProfile profile) {
        return getHighwayLevel(provider, profile, Highway.Z_HIGHWAY_LEVEL_CACHE, cp -> hasZHighway(cp, profile), Orientation.Z, coord);
    }

    private static int getHighwayLevel(IDimensionInfo provider, LostCityProfile profile, TimedCache<ChunkCoord, Integer> cache, Function<ChunkCoord, Boolean> hasHighway, Orientation orientation, ChunkCoord cp) {
        Integer cached = cache.get(cp);
        if (cached != null) {
            return cached;
        }

        // Highways can only occur at chunkZ that is a multiple of 8
        int mask = profile.HIGHWAY_DISTANCE_MASK;
        if (mask <= 0) {
            cache.put(cp, -1);
            return -1;
        }

        if ((cp.getCoord(orientation.getOpposite()) & mask) != 0) {
            cache.put(cp, -1);
            return -1;
        }

        // Disable highways that intersect with cityspheres
        if ((provider.getProfile().isSpace() || provider.getProfile().isSpheres()) && CitySphere.intersectsWithCitySphere(cp, provider)) {
            cache.put(cp, -1);
            return -1;
        }

        makePerlin(provider.getSeed());
        if (hasHighway.apply(cp)) {
            // This is part of a highway. Find the left-most chunk that is still part of this highway
            ChunkCoord lower = cp.lower(orientation);
            while (hasHighway.apply(lower)) {
                lower = lower.lower(orientation);
            }
            lower = lower.higher(orientation);     // This is now where the highway starts

            // Find the right-most chunk that is still part of this highway
            ChunkCoord higher = cp.higher(orientation);
            while (hasHighway.apply(higher)) {
                higher = higher.higher(orientation);
            }
            higher = higher.lower(orientation);     // This is now where the highway ends

            int level = -1;
            if (higher.getCoord(orientation)-lower.getCoord(orientation) >= 5) {
                boolean valid;
                if (profile.HIGHWAY_REQUIRES_TWO_CITIES) {
                    valid = BuildingInfo.isCityRaw(lower, provider, profile) && BuildingInfo.isCityRaw(higher, provider, profile);
                } else {
                    valid = BuildingInfo.isCityRaw(lower, provider, profile) || BuildingInfo.isCityRaw(higher, provider, profile);
                }
                if (valid) {
                    // We have at least one city. Valid highway:
                    level = switch (profile.HIGHWAY_LEVEL_FROM_CITIES_MODE) {
                        case 0 -> BuildingInfo.getCityLevel(lower, provider);
                        case 1 -> Math.min(BuildingInfo.getCityLevel(lower, provider),
                                BuildingInfo.getCityLevel(higher, provider));
                        case 2 -> Math.max(BuildingInfo.getCityLevel(lower, provider),
                                BuildingInfo.getCityLevel(higher, provider));
                        case 3 -> (BuildingInfo.getCityLevel(lower, provider) +
                                BuildingInfo.getCityLevel(higher, provider)) / 2;
                        default -> throw new RuntimeException("Bad value for 'highwayLevelFromCities'!");
                    };
                    for (ChunkCoord cc = lower; cc.getCoord(orientation) <= higher.getCoord(orientation); cc = cc.higher(orientation)) {
                        cache.put(cc, level);
                    }
                }
            }
            return level;

        }

        cache.put(cp, -1);
        return -1;
    }

    private static boolean hasXHighway(ChunkCoord cp, LostCityProfile profile) {
        return perlinX.getValue(cp.chunkX() / profile.HIGHWAY_MAINPERLIN_SCALE, cp.chunkZ() / profile.HIGHWAY_SECONDARYPERLIN_SCALE)
                > profile.HIGHWAY_PERLIN_FACTOR;
    }

    private static boolean hasZHighway(ChunkCoord cp, LostCityProfile profile) {
        return perlinZ.getValue(cp.chunkX() / profile.HIGHWAY_SECONDARYPERLIN_SCALE, cp.chunkZ() / profile.HIGHWAY_MAINPERLIN_SCALE)
                > profile.HIGHWAY_PERLIN_FACTOR;
    }

}
