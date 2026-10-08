package org.matsim.pt2matsim.lanes;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.matsim.api.core.v01.*;
import org.matsim.api.core.v01.network.*;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.population.routes.RouteUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.pt.transitSchedule.api.*;
import org.matsim.pt2matsim.config.PublicTransitMappingConfigGroup;
import org.matsim.pt2matsim.osm.LaneConversionReport;
import org.matsim.pt2matsim.tools.*;
import static org.junit.jupiter.api.Assertions.*;

class MappedTransitRouteRepairTest {
    private final Network network = NetworkUtils.createNetwork();
    private final TransitSchedule schedule = ScenarioUtils.createScenario(ConfigUtils.createConfig()).getTransitSchedule();
    private final PublicTransitMappingConfigGroup config = PublicTransitMappingConfigGroup.createDefaultConfig();

    private Link link(String id, String from, String to, String... modes) {
        var f = network.getFactory();
        for (String node : List.of(from, to)) if (!network.getNodes().containsKey(Id.createNodeId(node)))
            network.addNode(f.createNode(Id.createNodeId(node), new Coord(Integer.parseInt(node) * 100, 0)));
        Link link = f.createLink(Id.createLinkId(id), network.getNodes().get(Id.createNodeId(from)), network.getNodes().get(Id.createNodeId(to)));
        link.setLength(100); link.setFreespeed(10); link.setCapacity(1000); link.setNumberOfLanes(1);
        link.setAllowedModes(Set.of(modes)); network.addLink(link); return link;
    }

    private TransitRoute route(String... stopLinks) {
        return route(60, stopLinks);
    }

    private TransitRoute route(double interval, String... stopLinks) {
        var f = schedule.getFactory(); var stops = new ArrayList<TransitRouteStop>();
        for (int i = 0; i < stopLinks.length; i++) {
            var facility = f.createTransitStopFacility(Id.create("S" + i, TransitStopFacility.class), new Coord(i * 100, 0), false);
            facility.setLinkId(Id.createLinkId(stopLinks[i])); schedule.addStopFacility(facility);
            stops.add(f.createTransitRouteStop(facility, i * interval, i * interval));
        }
        var route = f.createTransitRoute(Id.create("R", TransitRoute.class), RouteUtils.createNetworkRoute(Arrays.stream(stopLinks).map(Id::createLinkId).toList()), stops, "bus");
        var line = f.createTransitLine(Id.create("L", TransitLine.class)); line.addRoute(route); schedule.addTransitLine(line);
        config.setMaxTravelCostFactor(10);
        return route;
    }

    @Test void checksTurnIntoDestinationAndFindsALegalDetour() {
        Link a = link("a", "0", "1", "bus"); Link b = link("b", "1", "2", "bus");
        link("d", "1", "4", "bus"); link("e", "4", "1", "bus");
        NetworkUtils.addDisallowedNextLinks(a, "bus", List.of(b.getId()));
        var route = route("a", "b"); var report = new LaneConversionReport();
        MappedTransitRouteRepair.repair(network, network, schedule, config, report);
        assertEquals(List.of("a", "d", "e", "b"), ScheduleTools.getTransitRouteLinkIds(route).stream().map(Object::toString).toList());
        assertEquals(1L, report.getCounts().get("restrictionRepairReroutedLegs"));
        assertEquals(-1, TurnRestrictedPathFinder.firstForbiddenEnd(network, ScheduleTools.getTransitRouteLinkIds(route), "bus"));
        assertEquals(List.of("a", "b"), route.getStops().stream().map(s -> s.getStopFacility().getLinkId().toString()).toList());
    }

    @Test void keepsViaWayHistoryAcrossStopsAndRestoresCleanedDetourLinks() {
        Link a = link("a", "0", "1", "bus"); Link b = link("b", "1", "2", "bus"); Link c = link("c", "2", "3", "bus");
        link("d", "2", "4", "bus"); link("e", "4", "2", "bus");
        NetworkUtils.addDisallowedNextLinks(a, "bus", List.of(b.getId(), c.getId()));
        var route = route("a", "b", "c"); var report = new LaneConversionReport();
        Network mapped = NetworkUtils.createNetwork(); NetworkTools.integrateNetwork(mapped, network, false);
        mapped.removeLink(Id.createLinkId("d")); mapped.removeLink(Id.createLinkId("e"));
        MappedTransitRouteRepair.repair(network, mapped, schedule, config, report);
        assertEquals(List.of("a", "b", "d", "e", "c"), ScheduleTools.getTransitRouteLinkIds(route).stream().map(Object::toString).toList());
        assertEquals(2L, report.getCounts().get("restoredRouteLinks"));
        assertEquals(List.of("a", "b", "c"), route.getStops().stream().map(s -> s.getStopFacility().getLinkId().toString()).toList());
    }

    @Test void reportsArtificialFallbackWhenNoLegalPathExists() {
        Link a = link("a", "0", "1", "bus"); Link b = link("b", "1", "2", "bus");
        NetworkUtils.addDisallowedNextLinks(a, "bus", List.of(b.getId()));
        var route = route("a", "b"); var report = new LaneConversionReport();
        MappedTransitRouteRepair.repair(network, network, schedule, config, report);
        assertEquals(1L, report.getCounts().get("restrictionRepairArtificialLegs"));
        assertEquals(-1, TurnRestrictedPathFinder.firstForbiddenEnd(network, ScheduleTools.getTransitRouteLinkIds(route), "bus"));
        assertEquals(List.of(b.getId()), NetworkUtils.getDisallowedNextLinks(a).getDisallowedLinkSequences("bus").getFirst());
    }

    @Test void doesNotRepairCarRestrictionsExemptingBusTraffic() {
        Link a = link("a", "0", "1", "bus", "car"); Link b = link("b", "1", "2", "bus", "car");
        NetworkUtils.addDisallowedNextLinks(a, "car", List.of(b.getId()));
        var route = route("a", "b"); var report = new LaneConversionReport();
        MappedTransitRouteRepair.repair(network, network, schedule, config, report);
        assertEquals(2, ScheduleTools.getTransitRouteLinkIds(route).size());
        assertNull(report.getCounts().get("restrictionRepairedTransitRoutes"));
    }

    @Test void obeysConfiguredCostLimit() {
        Link a = link("a", "0", "1", "bus"); Link b = link("b", "1", "2", "bus");
        link("d", "1", "4", "bus"); link("e", "4", "1", "bus");
        NetworkUtils.addDisallowedNextLinks(a, "bus", List.of(b.getId()));
        route("a", "b"); config.setMaxTravelCostFactor(1); var report = new LaneConversionReport();
        MappedTransitRouteRepair.repair(network, network, schedule, config, report);
        assertEquals(1L, report.getCounts().get("restrictionRepairArtificialLegs"));
    }

    @Test void zeroScheduledTravelTimeDoesNotProduceInfiniteFreeSpeed() {
        link("a", "0", "1", "bus", "artificial"); Link b = link("b", "1", "2", "bus", "artificial");
        route(0, "a", "b");
        ScheduleTools.setFreeSpeedBasedOnSchedule(network, schedule, Set.of("artificial"));
        assertEquals(10, b.getFreespeed());
    }
}
