package org.matsim.pt2matsim.hafas.filter;

import java.util.Set;
import java.util.TreeSet;
import org.matsim.pt2matsim.hafas.lib.FPLANRoute;

public class VehSystemFilter implements HafasFilter {

    private final Set<String> vehicleSystems;
    private final boolean includeTrainReplacementBus;
    private final Set<String> idsFilteredIn = new TreeSet<>();
    private final Set<String> idsFilteredOut = new TreeSet<>();

    public VehSystemFilter(Set<String> vehicleSystems, boolean includeTrainReplacementBus) {
        this.vehicleSystems = vehicleSystems;
        this.includeTrainReplacementBus = includeTrainReplacementBus;
    }

    @Override
    public Set<String> getIdsFilteredIn() {
        return this.idsFilteredIn;
    }

    @Override
    public Set<String> getIdsFilteredOut() {
        return this.idsFilteredOut;
    }

    @Override
    public boolean keepRoute(FPLANRoute route) {
        boolean keep;
        if (this.includeTrainReplacementBus && route.isRailReplacementBus() && route.getVehicleTypeId().toString().equals("B")) {
            keep = true;
        } else{
            keep = this.vehicleSystems.contains(route.getVehicleTypeId().toString());
        }
        if (keep) this.idsFilteredIn.add(route.getVehicleTypeId().toString());
        else this.idsFilteredOut.add(route.getVehicleTypeId().toString());
        return keep;
    }
}
