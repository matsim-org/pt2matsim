package org.matsim.pt2matsim.lanes;

import java.util.*;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.*;
import org.matsim.core.network.NetworkUtils;
import org.matsim.lanes.*;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt2matsim.osm.LaneConversionReport;
import org.matsim.pt2matsim.config.PublicTransitMappingStrings;
import org.matsim.pt2matsim.tools.ScheduleTools;

/** Reconciles lane definitions after mapping without reopening existing road movements. */
public final class MappedLanesReconciler {
    private MappedLanesReconciler() { }

    public static LaneConversionReport reconcile(Network network, Set<Id<Link>> originalLinks,
            Lanes lanes, TransitSchedule schedule) {
        return reconcile(network, originalLinks, lanes, schedule, new LaneConversionReport());
    }

    public static LaneConversionReport reconcile(Network network, Set<Id<Link>> originalLinks,
            Lanes lanes, TransitSchedule schedule, LaneConversionReport report) {
        // First attach only new connectors actually used by a route. Do this before
        // pruning leaves: a former dead end may now provide access to an artificial link.
        for (var line : schedule.getTransitLines().values()) for (var route : line.getRoutes().values()) {
            if (route.getRoute() == null) continue;
            List<Id<Link>> path = ScheduleTools.getTransitRouteLinkIds(route);
            for (int i = 0; i + 1 < path.size(); i++) {
                Link from = network.getLinks().get(path.get(i));
                Link to = network.getLinks().get(path.get(i + 1));
                if (from == null || to == null || originalLinks.contains(to.getId())
                        || !to.getAllowedModes().contains(PublicTransitMappingStrings.ARTIFICIAL_LINK_MODE) || !adjacent(from, to)
                        || !from.getAllowedModes().contains(route.getTransportMode())
                        || !to.getAllowedModes().contains(route.getTransportMode())
                        || forbidden(network, path, i, route.getTransportMode())) continue;
                var assignment = lanes.getLanesToLinkAssignments().get(from.getId());
                if (assignment == null) continue;
                for (Lane lane : assignment.getLanes().values()) {
                    if (lane.getToLaneIds() != null && !lane.getToLaneIds().isEmpty()) continue;
                    if (lane.getToLinkIds() == null || !lane.getToLinkIds().contains(to.getId())) {
                        lane.addToLinkId(to.getId());
                        report.add("inferredConnectorLaneConnections", "", from.getId(),
                                "lane=" + lane.getId() + "; to=" + to.getId() + "; mode=" + route.getTransportMode()
                                + "; new mapped connector available from each terminal lane");
                    }
                }
            }
        }
        var assignments = lanes.getLanesToLinkAssignments().entrySet().iterator();
        while (assignments.hasNext()) {
            var entry = assignments.next();
            Link link = network.getLinks().get(entry.getKey());
            if (link == null) {
                assignments.remove();
                report.add("removedLaneAssignments", "", entry.getKey(), "Link removed by mapper cleanup");
                continue;
            }
            var assignment = entry.getValue();
            for (Lane lane : assignment.getLanes().values()) if (lane.getToLinkIds() != null) {
                lane.getToLinkIds().removeIf(id -> {
                    Link target = network.getLinks().get(id);
                    if (target != null && adjacent(link, target) && locallyAllowedForAnyMode(link, target)) return false;
                    if (target != null && adjacent(link, target))
                        report.add("removedUnavailableLaneMovements", "", link.getId(),
                                "lane=" + lane.getId() + "; to=" + id + "; no common mode with a locally permitted turn after mapper cleanup");
                    report.add("removedLaneDestinations", "", link.getId(), "lane=" + lane.getId() + "; to=" + id);
                    return true;
                });
            }
            boolean changed;
            do {
                changed = false;
                var iterator = assignment.getLanes().values().iterator();
                while (iterator.hasNext()) {
                    Lane lane = iterator.next();
                    if (lane.getToLaneIds() != null) lane.getToLaneIds().removeIf(id -> !assignment.getLanes().containsKey(id));
                    if ((lane.getToLinkIds() == null || lane.getToLinkIds().isEmpty())
                            && (lane.getToLaneIds() == null || lane.getToLaneIds().isEmpty())) {
                        iterator.remove(); changed = true;
                        report.add("removedEmptyLanes", "", link.getId(), "lane=" + lane.getId());
                    }
                }
            } while (changed);
            if (assignment.getLanes().isEmpty()) {
                assignments.remove();
                report.add("removedLaneAssignments", "", link.getId(), "All destinations removed; no road movement inferred");
                continue;
            }
            for (Lane lane : assignment.getLanes().values()) checkLane(assignment, lane, new HashSet<>());
            LanesUtils.createLanes(link, assignment);
            report.add("validatedLaneAssignments", "", link.getId(), "Lane model valid after mapping");
        }
        auditRoutes(network, lanes, schedule, report);
        return report;
    }

    private static void checkLane(LanesToLinkAssignment assignment, Lane lane, Set<Id<Lane>> path) {
        if (!path.add(lane.getId())) throw new IllegalArgumentException("Cyclic lane topology on " + assignment.getLinkId());
        if (lane.getToLaneIds() != null) for (Id<Lane> id : lane.getToLaneIds()) {
            Lane next = assignment.getLanes().get(id);
            if (next == null || next.getStartsAtMeterFromLinkEnd() >= lane.getStartsAtMeterFromLinkEnd())
                throw new IllegalArgumentException("Invalid downstream lane " + id + " on " + assignment.getLinkId());
            checkLane(assignment, next, path);
        }
        path.remove(lane.getId());
    }

    private static boolean adjacent(Link from, Link to) {
        return from.getToNode().getId().equals(to.getFromNode().getId());
    }

    /** Keep shared lane connectivity whenever at least one mode can use it. */
    private static boolean locallyAllowedForAnyMode(Link from, Link to) {
        var restrictions = NetworkUtils.getDisallowedNextLinks(from);
        for (String mode : from.getAllowedModes()) {
            if (to.getAllowedModes().contains(mode) && (restrictions == null
                    || !restrictions.getDisallowedLinkSequences(mode).contains(List.of(to.getId())))) return true;
        }
        return false;
    }

    /** Checks the whole suffix, so restrictions spanning route-stop boundaries are caught. */
    private static boolean forbidden(Network network, List<Id<Link>> path, int start, String mode) {
        Link link = network.getLinks().get(path.get(start));
        if (link == null) return false;
        var restrictions = NetworkUtils.getDisallowedNextLinks(link);
        if (restrictions == null) return false;
        for (var sequence : restrictions.getDisallowedLinkSequences(mode)) {
            if (start + 1 + sequence.size() <= path.size()
                    && path.subList(start + 1, start + 1 + sequence.size()).equals(sequence)) return true;
        }
        return false;
    }

    private static void auditRoutes(Network network, Lanes lanes, TransitSchedule schedule, LaneConversionReport report) {
        for (var line : schedule.getTransitLines().values()) for (var route : line.getRoutes().values()) {
            String context = "line=" + line.getId() + "; route=" + route.getId() + "; mode=" + route.getTransportMode();
            if (route.getRoute() == null) {
                report.add("unmappedTransitRoutes", "", "", context); continue;
            }
            var path = ScheduleTools.getTransitRouteLinkIds(route);
            boolean invalid = false;
            for (int i = 0; i < path.size(); i++) {
                Link from = network.getLinks().get(path.get(i));
                if (from == null || !from.getAllowedModes().contains(route.getTransportMode())) {
                    report.add("invalidTransitRouteLinks", "", path.get(i), context); invalid = true; continue;
                }
                if (i + 1 >= path.size()) continue;
                Link to = network.getLinks().get(path.get(i + 1));
                if (to == null) continue;
                if (!adjacent(from, to)) {
                    report.add("nonAdjacentTransitMovements", "", from.getId(), context + "; to=" + to.getId()); invalid = true;
                }
                if (forbidden(network, path, i, route.getTransportMode())) {
                    report.add("restrictedTransitMovements", "", from.getId(), context + "; pathIndex=" + i); invalid = true;
                }
                var assignment = lanes.getLanesToLinkAssignments().get(from.getId());
                if (assignment != null && assignment.getLanes().values().stream()
                        .noneMatch(lane -> lane.getToLinkIds() != null && lane.getToLinkIds().contains(to.getId()))) {
                    report.add("unservedTransitLaneMovements", "", from.getId(), context + "; to=" + to.getId()); invalid = true;
                }
            }
            report.add(invalid ? "invalidMappedTransitRoutes" : "validatedMappedTransitRoutes", "", "", context);
        }
    }
}
