package synthlax.lostcitiesfork;

import synthlax.lostcitiesfork.api.ILostCitiesPre;
import synthlax.lostcitiesfork.api.ILostCityProfileSetup;

import java.util.function.Consumer;

public class LostCitiesPreImp implements ILostCitiesPre {

    @Override
    public void registerProfileSetupCallback(Consumer<ILostCityProfileSetup> runnable) {
        LostCities.setup.profileSetups.add(runnable);
    }
}
