package synthlax.lostcitiesfork.worldgen.lost.cityassets;

import synthlax.lostcitiesfork.api.ILostCityAsset;
import synthlax.lostcitiesfork.worldgen.lost.regassets.StuffSettingsRE;
import synthlax.lostcitiesfork.worldgen.lost.regassets.data.DataTools;
import net.minecraft.resources.ResourceLocation;

public class StuffObject implements ILostCityAsset {

    private final ResourceLocation name;
    private final StuffSettingsRE settings;

    public StuffObject(StuffSettingsRE settings) {
        this.settings = settings;
        this.name = settings.getRegistryName();
    }

    @Override
    public String getName() {
        return DataTools.toName(name);
    }

    @Override
    public ResourceLocation getId() {
        return name;
    }

    public StuffSettingsRE getSettings() {
        return settings;
    }
}
