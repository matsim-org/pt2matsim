package org.matsim.pt2matsim.hafas.filter;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt2matsim.hafas.lib.FPLANRoute;

public class StopsFilter extends AbstractFilter implements HafasFilter {

    private final StopFilterType stopFilterType;
    private final List<String> stopIds;

    public enum StopFilterType {FIRST_STOP, ANY_STOP, ALL_STOPS, FIRST_OR_LAST_STOP}

    public List<String> getStopIds() {
        return stopIds;
    }

    public StopsFilter(List<String> stopIds, StopFilterType stopFilterType) {
        this.stopIds = stopIds;
        this.stopFilterType = stopFilterType;
    }

    public StopsFilter(List<String> stopIds) {
        this.stopIds = stopIds;
        this.stopFilterType = StopFilterType.FIRST_OR_LAST_STOP;
    }

    @Override
    public boolean keepRoute(FPLANRoute route) {
        return keepRoute(route.getTransitRouteStops().stream().map(s -> s.getStopFacility().getId().toString()).toList());
    }

    public boolean keepRoute(TransitRoute route) {
        return keepRoute(route.getStops().stream().map(s -> s.getStopFacility().getId().toString()).toList());
    }

    public boolean keepRoute(List<String> routeStopIds) {
        boolean keep;
        switch (this.stopFilterType) {
            case FIRST_STOP -> {
                keep = this.stopIds.contains(routeStopIds.getFirst());
            }
            case FIRST_OR_LAST_STOP -> {
                keep = this.stopIds.contains(routeStopIds.getFirst()) || this.stopIds.contains(routeStopIds.getLast());
            }
            case ANY_STOP -> {
                keep = routeStopIds.stream().anyMatch(this.stopIds::contains);
            }
            case ALL_STOPS -> {
                keep = new HashSet<>(this.stopIds).containsAll(routeStopIds);
            }
            default -> {
                keep = false;
            }
        }
        if (keep) {
            this.getIdsFilteredIn().addAll(routeStopIds);
        } else {
            this.getIdsFilteredOut().addAll(routeStopIds);
        }
        return keep;
    }
}