package org.matsim.pt2matsim.hafas.filter;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.matsim.pt2matsim.hafas.lib.FPLANRoute;

public class StopsFilter implements HafasFilter {

    private final StopFilterType stopFilterType;
    private final Set<String> idsFilteredIn = new TreeSet<>();
    private final Set<String> idsFilteredOut = new TreeSet<>();


    public enum StopFilterType {FIRST_STOP, FIRST_OR_LAST_STOP};
    private final List<String> stops;

    public StopsFilter(List<String> stops, StopFilterType stopFilterType) {
        this.stops = stops;
        this.stopFilterType = stopFilterType;
    }

    public StopsFilter(List<String> stops) {
        this.stops = stops;
        this.stopFilterType = StopFilterType.FIRST_OR_LAST_STOP;
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
        switch (this.stopFilterType) {
            case FIRST_STOP -> {
                keep = this.stops.contains(route.getFirstStopId());
            }
            case FIRST_OR_LAST_STOP -> {
                keep = this.stops.contains(route.getFirstStopId()) || this.stops.contains(route.getLastStopId());
            }
            default -> {
                keep = false;
            }
        }
        if (keep) this.idsFilteredIn.add(route.getFirstStopId());
        else this.idsFilteredOut.add(route.getFirstStopId());
        return keep;
    }
}
