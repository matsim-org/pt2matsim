package org.matsim.pt2matsim.lanes;

import java.util.*;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.*;
import org.matsim.core.population.routes.RouteUtils;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.pt.transitSchedule.api.*;
import org.matsim.pt2matsim.config.*;
import org.matsim.pt2matsim.osm.LaneConversionReport;
import org.matsim.pt2matsim.tools.*;

/** Repairs mapped stop-to-stop legs using full restriction history and retaining every stop. */
public final class MappedTransitRouteRepair {
    private MappedTransitRouteRepair() { }

    public static void repair(Network routingNetwork, Network mappedNetwork, TransitSchedule schedule,
            PublicTransitMappingConfigGroup config, LaneConversionReport report) {
        for (var line : schedule.getTransitLines().values()) for (var route : line.getRoutes().values()) {
            if (route.getRoute() == null) continue;
            List<Id<Link>> path = ScheduleTools.getTransitRouteLinkIds(route);
            int repairs = 0;
            while (true) {
                int badEnd = TurnRestrictedPathFinder.firstForbiddenEnd(routingNetwork, path, route.getTransportMode());
                if (badEnd < 0) break;
                if (++repairs > 1000) throw new IllegalStateException("Cannot converge on legal route " + route.getId());
                List<Integer> positions = stopPositions(route, path);
                int leg = -1;
                for (int i = 0; i + 1 < positions.size(); i++)
                    if (positions.get(i) < badEnd && positions.get(i + 1) >= badEnd) { leg = i; break; }
                if (leg < 0) throw new IllegalStateException("Restriction outside stop legs on route " + route.getId());
                int start = positions.get(leg), end = positions.get(leg + 1);
                Link from = routingNetwork.getLinks().get(path.get(start));
                Link to = routingNetwork.getLinks().get(path.get(end));
                double minCost = PTMapperTools.calcMinTravelCost(route.getStops().get(leg), route.getStops().get(leg + 1), config.getTravelCostType());
                if (minCost == 0) minCost = CoordUtils.calcEuclideanDistance(
                        route.getStops().get(leg).getStopFacility().getCoord(), route.getStops().get(leg + 1).getStopFacility().getCoord()) / 10;
                double maxCost = config.getMaxTravelCostFactor() * minCost;
                List<Id<Link>> replacement = TurnRestrictedPathFinder.find(routingNetwork,
                        path.subList(0, start + 1), to, route.getTransportMode(), config.getTravelCostType(), maxCost);
                String context = "line=" + line.getId() + "; route=" + route.getId() + "; mode=" + route.getTransportMode()
                        + "; stopLeg=" + leg + "; to=" + to.getId();
                if (replacement == null) {
                    Link connector = connector(routingNetwork, from, to, route.getTransportMode());
                    replacement = List.of(connector.getId());
                    report.add("restrictionRepairArtificialLegs", "", from.getId(), context + "; no legal path within configured cost bound");
                } else report.add("restrictionRepairReroutedLegs", "", from.getId(), context);
                List<Id<Link>> repaired = new ArrayList<>(path.subList(0, start + 1));
                repaired.addAll(replacement); repaired.addAll(path.subList(end, path.size())); path = repaired;
            }
            if (repairs > 0) {
                route.setRoute(RouteUtils.createNetworkRoute(path));
                report.add("restrictionRepairedTransitRoutes", "", "", "line=" + line.getId() + "; route=" + route.getId());
            }
            // Restore physical links used by detours that the mapper previously cleaned away.
            for (Id<Link> id : path) if (!mappedNetwork.getLinks().containsKey(id)) {
                Link source = routingNetwork.getLinks().get(id);
                for (Node node : List.of(source.getFromNode(), source.getToNode())) if (!mappedNetwork.getNodes().containsKey(node.getId()))
                    mappedNetwork.addNode(mappedNetwork.getFactory().createNode(node.getId(), node.getCoord()));
                Link copy = mappedNetwork.getFactory().createLink(id, mappedNetwork.getNodes().get(source.getFromNode().getId()), mappedNetwork.getNodes().get(source.getToNode().getId()));
                copy.setLength(source.getLength()); copy.setCapacity(source.getCapacity()); copy.setFreespeed(source.getFreespeed());
                copy.setNumberOfLanes(source.getNumberOfLanes()); copy.setAllowedModes(new HashSet<>(source.getAllowedModes()));
                source.getAttributes().getAsMap().forEach(copy.getAttributes()::putAttribute);
                mappedNetwork.addLink(copy);
                report.add("restoredRouteLinks", "", id, "Physical detour or new restriction-repair connector");
            }
        }
        ScheduleTools.assignScheduleModesToLinks(schedule, mappedNetwork);
        ScheduleTools.setFreeSpeedBasedOnSchedule(mappedNetwork, schedule, config.getScheduleFreespeedModes());
    }

    private static List<Integer> stopPositions(TransitRoute route, List<Id<Link>> path) {
        List<Integer> positions = new ArrayList<>(); int cursor = 0;
        for (var stop : route.getStops()) {
            Id<Link> id = stop.getStopFacility().getLinkId();
            while (cursor < path.size() && !path.get(cursor).equals(id)) cursor++;
            if (cursor == path.size()) throw new IllegalArgumentException("Stop missing from route " + route.getId() + ": " + id);
            positions.add(cursor);
        }
        return positions;
    }

    private static Link connector(Network network, Link from, Link to, String mode) {
        Id<Link> id = Id.createLinkId("ptRestrictionRepair:" + from.getId() + ":" + to.getId());
        Link link = network.getLinks().get(id);
        if (link == null) {
            link = network.getFactory().createLink(id, from.getToNode(), to.getFromNode());
            link.setLength(Math.max(1, CoordUtils.calcEuclideanDistance(from.getToNode().getCoord(), to.getFromNode().getCoord())));
            link.setNumberOfLanes(1); link.setCapacity(9999); link.setFreespeed(10);
            link.setAllowedModes(Set.of(PublicTransitMappingStrings.ARTIFICIAL_LINK_MODE, mode));
            network.addLink(link);
        } else {
            Set<String> modes = new HashSet<>(link.getAllowedModes()); modes.add(mode); link.setAllowedModes(modes);
        }
        return link;
    }
}
