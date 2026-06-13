package synthlax.lostcitiesfork.worldgen.lost.cityassets;

import synthlax.lostcitiesfork.LostCities;
import synthlax.lostcitiesfork.api.ILostCityAsset;
import synthlax.lostcitiesfork.api.ILostCityAssetRegistry;
import synthlax.lostcitiesfork.worldgen.lost.regassets.IAsset;
import synthlax.lostcitiesfork.worldgen.lost.regassets.data.DataTools;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.CommonLevelAccessor;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.function.Function;

public class RegistryAssetRegistry<T extends ILostCityAsset, R> implements ILostCityAssetRegistry<T>  {

    private final Map<ResourceLocation, T> assets = new java.util.concurrent.ConcurrentHashMap<>();
    private final ResourceKey<Registry<R>> registryKey;
    private final Function<R, T> assetConstructor;

    public <S extends ILostCityAsset> ILostCityAssetRegistry<S> cast() {
        return (ILostCityAssetRegistry<S>) this;
    }

    public RegistryAssetRegistry(ResourceKey<Registry<R>> registryKey, Function<R, T> assetConstructor) {
        this.registryKey = registryKey;
        this.assetConstructor = assetConstructor;
    }

    @Override
    public T get(CommonLevelAccessor level, String name) {
        if (name == null) {
            return null;
        }
        return get(level, DataTools.fromName(name));
    }

    @Nonnull
    public T getOrThrow(CommonLevelAccessor level, String name) {
        if (name == null) {
            throw new RuntimeException("Invalid name given to " + registryKey.registry() + " getOrThrow!");
        }
        T result = get(level, DataTools.fromName(name));
        if (result == null) {
            throw new RuntimeException("Can't find '" + name + "' in " + registryKey.registry() + "!");
        }
        return result;
    }

    @Nullable
    public T getOrWarn(CommonLevelAccessor level, String name) {
        if (name == null || name.trim().isEmpty()) {
            return null;
        }
        T rc = get(level, DataTools.fromName(name));
        if (rc == null) {
            // Warning
            LostCities.LOGGER.warn("Cannot find '" + name + "' in " + registryKey.registry() + "!");
        }
        return rc;
    }

    @Override
    public T get(CommonLevelAccessor level, ResourceLocation name) {
        if (name == null) {
            return null;
        }
        T t = assets.get(name);
        if (t == null) {
            synchronized (this) {
                t = assets.get(name);
                if (t == null) {
                    try {
                        Registry<R> registry = level.registryAccess().registryOrThrow(registryKey);
                        R value = registry.get(ResourceKey.create(registryKey, name));
                        if (value instanceof IAsset asset) {
                            asset.setRegistryName(name);
                        }
                        t = assetConstructor.apply(value);
                    } catch (Exception e) {
                        throw new RuntimeException("Error getting resource " + name + "!", e);
                    }
                    if (t != null) {
                        assets.put(name, t);
                    }
                }
            }
        }
        if (t != null) {
            t.init(level);
        }
        return t;
    }

    public synchronized void loadAll(CommonLevelAccessor level) {
        if (level == null) {
            return;
        }
        Registry<R> registry = level.registryAccess().registryOrThrow(registryKey);
        for (R r : registry) {
            ResourceLocation name = registry.getKey(r);
            if (!assets.containsKey(name)) {
                if (r instanceof IAsset asset) {
                    asset.setRegistryName(name);
                }
                T t = assetConstructor.apply(r);
                if (t != null) {
                    assets.put(name, t);
                }
            }
        }
    }

    @Override
    public Iterable<T> getIterable() {
        return assets.values();
    }

    public int getNumAssets(CommonLevelAccessor level) {
        return level.registryAccess().registryOrThrow(registryKey).size();
    }

    public synchronized void reset() {
        assets.clear();
    }
}
