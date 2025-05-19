package org.matsim.pt2matsim.hafas.filter;

import java.util.Set;
import java.util.TreeSet;
import org.matsim.pt2matsim.hafas.lib.FPLANRoute;

public class VehSystemFilter extends AbstractFilter implements HafasFilter {

    private final Set<String> vehicleSystems;
    private final boolean includeTrainReplacementBus;

    public VehSystemFilter(Set<String> vehicleSystems, boolean includeTrainReplacementBus) {
        this.vehicleSystems = vehicleSystems;
        this.includeTrainReplacementBus = includeTrainReplacementBus;
    }

    @Override
    public boolean keepRoute(FPLANRoute route) {
        boolean keep;
        if (this.includeTrainReplacementBus && route.isRailReplacementBus() && route.getVehicleTypeId().toString().equals("B")) {
            keep = true;
        } else{
            keep = this.vehicleSystems.contains(route.getVehicleTypeId().toString());
        }
        if (keep) this.getIdsFilteredIn().add(route.getVehicleTypeId().toString());
        else this.getIdsFilteredOut().add(route.getVehicleTypeId().toString());
        return keep;
    }
}
