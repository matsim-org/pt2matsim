package org.matsim.pt2matsim.lanes;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.matsim.api.core.v01.*;
import org.matsim.api.core.v01.network.*;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.population.routes.RouteUtils;
import org.matsim.lanes.*;
import org.matsim.pt.transitSchedule.api.*;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;
import static org.junit.jupiter.api.Assertions.*;

class MappedLanesReconcilerTest {
    private final Network network = NetworkUtils.createNetwork();
    private final Lanes lanes = LanesUtils.createLanesContainer();
    private final TransitSchedule schedule = ScenarioUtils.createScenario(ConfigUtils.createConfig()).getTransitSchedule();

    private Link link(String id, String from, String to, String... modes) {
        var f = network.getFactory();
        for (String node : List.of(from, to)) if (!network.getNodes().containsKey(Id.createNodeId(node)))
            network.addNode(f.createNode(Id.createNodeId(node), new Coord(network.getNodes().size() * 100, 0)));
        Link link = f.createLink(Id.createLinkId(id), network.getNodes().get(Id.createNodeId(from)), network.getNodes().get(Id.createNodeId(to)));
        link.setLength(100); link.setFreespeed(10); link.setCapacity(1000); link.setNumberOfLanes(1);
        link.setAllowedModes(Set.of(modes)); network.addLink(link); return link;
    }

    private Lane assignment(Link link, String... destinations) {
        var a = lanes.getFactory().createLanesToLinkAssignment(link.getId());
        Lane original = lanes.getFactory().createLane(Id.create(link.getId() + ".ol", Lane.class));
        original.setStartsAtMeterFromLinkEnd(100); original.setCapacityVehiclesPerHour(1000); original.setNumberOfRepresentedLanes(1);
        Lane terminal = lanes.getFactory().createLane(Id.create(link.getId() + ".1", Lane.class));
        terminal.setStartsAtMeterFromLinkEnd(50); terminal.setCapacityVehiclesPerHour(1000); terminal.setNumberOfRepresentedLanes(1);
        for (String target : destinations) terminal.addToLinkId(Id.createLinkId(target));
        original.addToLaneId(terminal.getId()); a.addLane(original); a.addLane(terminal); lanes.addLanesToLinkAssignment(a);
        return terminal;
    }

    private void route(String mode, String... links) {
        var f = schedule.getFactory();
        TransitLine line = f.createTransitLine(Id.create("L", TransitLine.class));
        var path = RouteUtils.createLinkNetworkRouteImpl(Id.createLinkId(links[0]), Id.createLinkId(links[links.length - 1]));
        path.setLinkIds(path.getStartLinkId(), Arrays.stream(links).skip(1).limit(links.length - 2).map(Id::createLinkId).toList(), path.getEndLinkId());
        line.addRoute(f.createTransitRoute(Id.create("R", TransitRoute.class), path, List.of(), mode)); schedule.addTransitLine(line);
    }

    @Test void addsOnlyUsedNewConnectorsAndKeepsExistingRoadMovements() {
        Link a = link("a", "0", "1", "bus", "car");
        link("road", "1", "2", "bus", "car");
        link("connector", "1", "3", "bus", "artificial");
        link("unused", "1", "4", "bus", "artificial");
        Lane terminal = assignment(a, "road", "removed"); route("bus", "a", "connector");
        var report = MappedLanesReconciler.reconcile(network, Set.of(a.getId(), Id.createLinkId("road")), lanes, schedule);
        assertEquals(Set.of(Id.createLinkId("road"), Id.createLinkId("connector")), new HashSet<>(terminal.getToLinkIds()));
        assertEquals(1L, report.getCounts().get("inferredConnectorLaneConnections"));
        assertEquals(1L, report.getCounts().get("removedLaneDestinations"));
        assertEquals(1L, report.getCounts().get("validatedMappedTransitRoutes"));
    }

    @Test void doesNotReopenAnExistingUnservedRoadTurn() {
        Link a = link("a", "0", "1", "bus");
        link("legal", "1", "2", "bus"); Link forbidden = link("forbidden", "1", "3", "bus");
        Lane terminal = assignment(a, "legal"); route("bus", "a", "forbidden");
        NetworkUtils.addDisallowedNextLinks(a, "bus", List.of(forbidden.getId()));
        var report = MappedLanesReconciler.reconcile(network, Set.copyOf(network.getLinks().keySet()), lanes, schedule);
        assertEquals(List.of(Id.createLinkId("legal")), terminal.getToLinkIds());
        assertEquals(1L, report.getCounts().get("restrictedTransitMovements"));
        assertEquals(1L, report.getCounts().get("unservedTransitLaneMovements"));
        assertEquals(1L, report.getCounts().get("invalidMappedTransitRoutes"));
    }

    @Test void auditsFullViaWayRestrictionsAndRespectsBusExemptions() {
        Link a = link("a", "0", "1", "bus", "car");
        Link b = link("b", "1", "2", "bus", "car"); Link c = link("c", "2", "3", "bus", "car");
        assignment(a, "b"); assignment(b, "c"); route("bus", "a", "b", "c");
        NetworkUtils.addDisallowedNextLinks(a, "car", List.of(b.getId(), c.getId()));
        var exempt = MappedLanesReconciler.reconcile(network, Set.copyOf(network.getLinks().keySet()), lanes, schedule);
        assertEquals(1L, exempt.getCounts().get("validatedMappedTransitRoutes"));
        NetworkUtils.addDisallowedNextLinks(a, "bus", List.of(b.getId(), c.getId()));
        var restricted = MappedLanesReconciler.reconcile(network, Set.copyOf(network.getLinks().keySet()), lanes, schedule);
        assertEquals(1L, restricted.getCounts().get("restrictedTransitMovements"));
    }

    @Test void removesEmptyLaneTreesAndAssignmentsOnDeletedLinks() {
        Link a = link("a", "0", "1", "bus");
        Link removed = link("removed", "1", "2", "bus");
        assignment(a, "removed"); assignment(removed, "gone"); network.removeLink(removed.getId());
        var report = MappedLanesReconciler.reconcile(network, Set.of(a.getId(), removed.getId()), lanes, schedule);
        assertTrue(lanes.getLanesToLinkAssignments().isEmpty());
        assertEquals(2L, report.getCounts().get("removedLaneAssignments"));
        assertEquals(2L, report.getCounts().get("removedEmptyLanes"));
    }

    @Test void removesConnectionsWhoseOnlyModeWasRemovedByMapperCleanup() {
        Link a = link("a", "0", "1", "car", "bus");
        link("road", "1", "2", "car");
        link("busLane", "1", "3", "bus");
        Lane terminal = assignment(a, "road", "busLane");
        a.setAllowedModes(Set.of("car"));
        route("car", "a", "road");
        var report = MappedLanesReconciler.reconcile(network, Set.copyOf(network.getLinks().keySet()), lanes, schedule);
        assertEquals(List.of(Id.createLinkId("road")), terminal.getToLinkIds());
        assertEquals(1L, report.getCounts().get("removedUnavailableLaneMovements"));
        assertEquals(1L, report.getCounts().get("validatedMappedTransitRoutes"));
    }

    @Test void retainsBusExemptMovementButPrunesMovementBannedForEveryRemainingMode() {
        Link a = link("a", "0", "1", "car", "bus");
        Link exempt = link("exempt", "1", "2", "car", "bus");
        Link forbidden = link("forbidden", "1", "3", "car", "bus");
        Lane terminal = assignment(a, "exempt", "forbidden");
        NetworkUtils.addDisallowedNextLinks(a, "car", List.of(exempt.getId()));
        for (String mode : List.of("car", "bus")) NetworkUtils.addDisallowedNextLinks(a, mode, List.of(forbidden.getId()));
        route("bus", "a", "exempt");
        var report = MappedLanesReconciler.reconcile(network, Set.copyOf(network.getLinks().keySet()), lanes, schedule);
        assertEquals(List.of(exempt.getId()), terminal.getToLinkIds());
        assertEquals(1L, report.getCounts().get("removedUnavailableLaneMovements"));
        assertEquals(1L, report.getCounts().get("validatedMappedTransitRoutes"));
    }
}
