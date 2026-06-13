package synthlax.lostcitiesfork.worldgen.lost.regassets;

import net.minecraft.resources.ResourceLocation;

public interface IAsset<T extends IAsset> {
    T setRegistryName(ResourceLocation name);
}
