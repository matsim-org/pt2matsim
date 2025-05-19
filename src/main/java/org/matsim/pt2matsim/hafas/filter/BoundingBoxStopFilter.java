package org.matsim.pt2matsim.hafas.filter;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.matsim.api.core.v01.Coord;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitRouteStop;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;
import org.matsim.pt2matsim.hafas.filter.StopsFilter.StopFilterType;
import org.matsim.pt2matsim.hafas.lib.FPLANRoute;

public class BoundingBoxStopFilter extends AbstractFilter implements HafasFilter {

    private final StopFilterType stopFilterType;
    private final BoundingBox boundingBox;

    public record BoundingBox (double north, double south, double east, double west) {}

    public BoundingBoxStopFilter(BoundingBox boundingBox, StopFilterType stopFilterType) {
        this.boundingBox = boundingBox;
        this.stopFilterType = stopFilterType;
    }

    public BoundingBoxStopFilter(BoundingBox boundingBox) {
        this.boundingBox = boundingBox;
        this.stopFilterType = StopFilterType.FIRST_OR_LAST_STOP;
    }

    @Override
    public boolean keepRoute(FPLANRoute route) {
        return keepRoute(route.getTransitRouteStops().stream().map(TransitRouteStop::getStopFacility).toList());
    }

    public boolean keepRoute(TransitRoute route) {
        return keepRoute(route.getStops().stream().map(TransitRouteStop::getStopFacility).toList());
    }

    public boolean keepRoute(List<TransitStopFacility> routeStopFacilities) {
        boolean keep;
        switch (this.stopFilterType) {
            case FIRST_STOP -> {
                keep = stopInBoundingBox(routeStopFacilities.getFirst());
            }
            case FIRST_OR_LAST_STOP -> {
                keep = stopInBoundingBox(routeStopFacilities.getFirst()) || stopInBoundingBox(routeStopFacilities.getLast());
            }
            case ANY_STOP -> {
                keep = routeStopFacilities.stream().anyMatch(this::stopInBoundingBox);
            }
            case ALL_STOPS -> {
                keep = routeStopFacilities.stream().allMatch(this::stopInBoundingBox);
            }
            default -> {
                keep = false;
            }
        }
        List<String> routeStopIds = routeStopFacilities.stream().map(s -> s.getId().toString()).toList();
        if (keep) {
            this.getIdsFilteredIn().addAll(routeStopIds);
        } else {
            this.getIdsFilteredOut().addAll(routeStopIds);
        }
        return keep;
    }

    public boolean stopInBoundingBox(TransitStopFacility stop) {
        return pointInBoundingBox(stop.getCoord());
    }

    private boolean pointInBoundingBox(Coord coord) {
        return (
                coord.getY() >= this.boundingBox.south() &&
                coord.getY() <= this.boundingBox.north() &&
                coord.getX() >= this.boundingBox.west() &&
                coord.getX() <= this.boundingBox.east()
            );
    }
}