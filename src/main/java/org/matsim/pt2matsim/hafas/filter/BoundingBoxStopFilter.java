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

public class BoundingBoxStopFilter implements HafasFilter {

    private final StopFilterType stopFilterType;
    private final Set<String> idsFilteredIn = new TreeSet<>();
    private final Set<String> idsFilteredOut = new TreeSet<>();

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
    public Set<String> getIdsFilteredIn() {
        return this.idsFilteredIn;
    }

    @Override
    public Set<String> getIdsFilteredOut() {
        return this.idsFilteredOut;
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
        List<Coord> routeStopCoords = routeStopFacilities.stream().map(TransitStopFacility::getCoord).toList();
        switch (this.stopFilterType) {
            case FIRST_STOP -> {
                keep = pointInBoundingBox(routeStopCoords.getFirst(), this.boundingBox);
            }
            case FIRST_OR_LAST_STOP -> {
                keep = pointInBoundingBox(routeStopCoords.getFirst(), this.boundingBox) || pointInBoundingBox(routeStopCoords.getLast(), this.boundingBox);
            }
            case ANY_STOP -> {
                keep = routeStopCoords.stream().anyMatch(point -> pointInBoundingBox(point, this.boundingBox));
            }
            case ALL_STOPS -> {
                keep = routeStopCoords.stream().allMatch(point -> pointInBoundingBox(point, this.boundingBox));
            }
            default -> {
                keep = false;
            }
        }
        List<String> routeStopIds = routeStopFacilities.stream().map(s -> s.getId().toString()).toList();
        if (keep) {
            this.idsFilteredIn.addAll(routeStopIds);
        } else {
            this.idsFilteredOut.addAll(routeStopIds);
        }
        return keep;
    }

    private boolean pointInBoundingBox(Coord coord, BoundingBox boundingBox) {
        return (
                coord.getY() >= boundingBox.south() &&
                coord.getY() <= boundingBox.north() &&
                coord.getX() >= boundingBox.west() &&
                coord.getX() <= boundingBox.east()
            );
    }
}