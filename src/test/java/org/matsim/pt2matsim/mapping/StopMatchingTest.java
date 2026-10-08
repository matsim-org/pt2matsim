package org.matsim.pt2matsim.mapping;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.matsim.api.core.v01.*;
import org.matsim.api.core.v01.network.*;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.network.turnRestrictions.DisallowedNextLinksUtils;
import org.matsim.pt.transitSchedule.api.*;
import org.matsim.pt.utils.TransitScheduleValidator;
import org.matsim.pt2matsim.config.*;
import org.matsim.pt2matsim.mapping.stopMatching.*;
import org.matsim.pt2matsim.tools.ScheduleTools;

class StopMatchingTest {
    @TempDir Path temporary;
    private final Network network = NetworkUtils.createNetwork();
    private final TransitSchedule schedule = ScenarioUtils.createScenario(ConfigUtils.createConfig()).getTransitSchedule();

    private Link link(String id, String from, double x1, double y1, String to, double x2, double y2, String... modes) {
        var f = network.getFactory();
        for (var node : List.of(Map.entry(from, new Coord(x1, y1)), Map.entry(to, new Coord(x2, y2))))
            if (!network.getNodes().containsKey(Id.createNodeId(node.getKey())))
                network.addNode(f.createNode(Id.createNodeId(node.getKey()), node.getValue()));
        Link link = f.createLink(Id.createLinkId(id), network.getNodes().get(Id.createNodeId(from)), network.getNodes().get(Id.createNodeId(to)));
        link.setLength(400); link.setFreespeed(10); link.setNumberOfLanes(2); link.setCapacity(2000);
        link.setAllowedModes(Set.of(modes)); network.addLink(link); return link;
    }

    private void route(double[][] coordinates) {
        var factory = schedule.getFactory(); List<TransitRouteStop> stops = new ArrayList<>();
        for (int i = 0; i < coordinates.length; i++) {
            var stop = factory.createTransitStopFacility(Id.create("s" + i, TransitStopFacility.class), new Coord(coordinates[i][0], coordinates[i][1]), false);
            schedule.addStopFacility(stop); stops.add(factory.createTransitRouteStop(stop, i * 120, i * 120 + 10));
        }
        var line = factory.createTransitLine(Id.create("line", TransitLine.class));
        var route = factory.createTransitRoute(Id.create("route", TransitRoute.class), null, stops, "bus");
        route.addDeparture(factory.createDeparture(Id.create("departure", Departure.class), 3600));
        line.addRoute(route); schedule.addTransitLine(line);
    }

    private PublicTransitMappingConfigGroup config(String... rows) throws Exception {
        Path csv = temporary.resolve("geometry.csv");
        Files.writeString(csv, "LinkId,Geometry\n" + String.join("\n", rows) + "\n");
        var config = new PublicTransitMappingConfigGroup();
        config.getModesToKeepOnCleanUp().add("car");
        var bus = new TransportModeParameterSet("bus"); bus.setNetworkModesStr("bus"); config.addParameterSet(bus);
        config.setInputNetworkGeometryFile(csv.toString()); config.setSplitLinksAtStops(true);
        config.setNumOfThreads(1); config.setMaxLinkCandidateDistance(10); config.setNLinkThreshold(2);
        config.setStopMatchingReportFile(temporary.resolve("audit.csv").toString());
        return config;
    }

    @Test void mapsTwoStopsOnOneCurvedRoadWithoutArtificialLinks() throws Exception {
        Link road = link("road", "a", 0, 0, "b", 200, 0, "bus", "car");
        route(new double[][] {{50, 101}, {150, 102}});
        var config = config("road,\"LINESTRING (0 0, 0 100, 200 100, 200 0)\"");
        config.setOutputPreparedNetworkFile(temporary.resolve("prepared.xml.gz").toString());
        new PTMapper(schedule, network).run(config);
        assertTrue(Files.exists(temporary.resolve("prepared.xml.gz.geometry.csv")));
        assertTrue(TransitScheduleValidator.validateAll(schedule, network).isValid());
        assertTrue(network.getLinks().values().stream().noneMatch(l -> l.getAllowedModes().contains("artificial")));
        var route = schedule.getTransitLines().values().iterator().next().getRoutes().values().iterator().next();
        assertNotEquals(route.getStops().get(0).getStopFacility().getLinkId(), route.getStops().get(1).getStopFacility().getLinkId());
        assertEquals(10, route.getStops().get(0).getDepartureOffset().seconds());
        assertEquals(130, route.getStops().get(1).getDepartureOffset().seconds());
        assertEquals(1, route.getDepartures().size());
        assertEquals(road.getLength(), network.getLinks().values().stream().mapToDouble(Link::getLength).sum(), 1e-8);
        for (Link fragment : network.getLinks().values()) {
            assertEquals(2000, fragment.getCapacity()); assertEquals(2, fragment.getNumberOfLanes());
        }
    }

    @Test void remapsSingleTurnsAndFullViaWaySequencesToFragments() throws Exception {
        Link from = link("from", "start", -100, 0, "a", 0, 0, "bus", "car");
        link("road", "a", 0, 0, "b", 200, 0, "bus", "car");
        Link exit = link("exit", "b", 200, 0, "end", 300, 0, "bus", "car");
        NetworkUtils.addDisallowedNextLinks(from, "car", List.of(Id.createLinkId("road")));
        NetworkUtils.addDisallowedNextLinks(from, "bus", List.of(Id.createLinkId("road"), exit.getId()));
        route(new double[][] {{50, 100}, {150, 100}});
        var geometry = StopLinkPreparation.prepare(network, schedule, config("road,\"LINESTRING (0 0, 0 100, 200 100, 200 0)\""));
        var car = NetworkUtils.getDisallowedNextLinks(from).getDisallowedLinkSequences("car").get(0);
        var bus = NetworkUtils.getDisallowedNextLinks(from).getDisallowedLinkSequences("bus").get(0);
        assertEquals(List.of(Id.createLinkId("stopMatch:road:1")), car);
        assertEquals(List.of(Id.createLinkId("stopMatch:road:1"), Id.createLinkId("stopMatch:road:2"), Id.createLinkId("road"), exit.getId()), bus);
        assertTrue(DisallowedNextLinksUtils.isValid(network));
        geometry.write(temporary.resolve("output.csv").toString());
        new LinkGeometryIndex(network, temporary.resolve("output.csv").toString()); // Directed endpoints still match.
    }

    @Test void oppositeRoadsAndReservedLinksGetIndependentInternalNodes() throws Exception {
        link("forward", "a", 0, 0, "b", 200, 0, "bus", "car");
        link("backward", "b", 200, 0, "a", 0, 0, "bus", "car");
        link("reserved", "a", 0, 0, "b", 200, 0, "bus");
        link("carOnlyParallel", "a", 0, 0, "b", 200, 0, "car");
        for (Link road : network.getLinks().values()) road.getAttributes().putAttribute("osm:way:id", "42");
        route(new double[][] {{50, 1}, {150, 1}});
        StopLinkPreparation.prepare(network, schedule, config());
        assertEquals(3, network.getLinks().values().stream().filter(l -> "carOnlyParallel".equals(
                l.getAttributes().getAttribute(StopLinkPreparation.ORIGINAL_LINK))).count());
        assertFalse(network.getLinks().get(Id.createLinkId("carOnlyParallel")).getAllowedModes().contains("bus"));
        for (Node node : network.getNodes().values()) if (node.getId().toString().startsWith("stopMatch:")) {
            assertEquals(1, node.getInLinks().size()); assertEquals(1, node.getOutLinks().size());
            assertEquals(node.getInLinks().values().iterator().next().getAttributes().getAttribute(StopLinkPreparation.ORIGINAL_LINK),
                         node.getOutLinks().values().iterator().next().getAttributes().getAttribute(StopLinkPreparation.ORIGINAL_LINK));
        }
    }

    @Test void respectsModeAccessAndKeepsGenuineMissingRoadFallback() throws Exception {
        link("carOnly", "a", 0, 0, "b", 200, 0, "car");
        route(new double[][] {{50, 1}, {150, 1}});
        var config = config();
        new PTMapper(schedule, network).run(config);
        assertEquals(4, network.getNodes().size()); // Two original nodes plus two artificial stop nodes.
        assertTrue(network.getLinks().values().stream().anyMatch(l -> l.getAllowedModes().contains("artificial")));
        assertFalse(network.getLinks().values().stream().anyMatch(l -> l.getAttributes().getAttribute(StopLinkPreparation.ORIGINAL_LINK) != null));
        assertTrue(Files.readString(temporary.resolve("audit.csv")).contains("noEligibleRoadCandidate"));
    }

    @Test void rejectsGeometryFromAnotherNetworkOrCoordinateSystem() throws Exception {
        link("road", "a", 0, 0, "b", 200, 0, "bus");
        route(new double[][] {{50, 1}, {150, 1}});
        var config = config("road,\"LINESTRING (0 0, 0 100, 201 0)\"");
        assertThrows(IllegalArgumentException.class, () -> StopLinkPreparation.prepare(network, schedule, config));
    }

    @Test void degenerateExporterGeometryFallsBackToEndpointsAndIsReported() throws Exception {
        Link road = link("road", "a", 0, 0, "b", 200, 0, "bus");
        route(new double[][] {{50, 1}, {150, 1}});
        var config = config("road,\"LINESTRING (0 0)\"");
        var index = new LinkGeometryIndex(network, config.getInputNetworkGeometryFile());
        assertEquals(1, index.getFallbackShapes());
        assertEquals(1, index.distance(road, new Coord(50, 1)), 1e-9);
    }

    @Test void configurationRoundTripAndLegacyDefaults() throws Exception {
        var defaults = PublicTransitMappingConfigGroup.createDefaultConfig();
        assertFalse(defaults.getSplitLinksAtStops()); assertNull(defaults.getInputNetworkGeometryFile());
        defaults.writeToFile(temporary.resolve("defaults.xml").toString());
        var defaultRoundTrip = PublicTransitMappingConfigGroup.loadConfig(temporary.resolve("defaults.xml").toString());
        assertNull(defaultRoundTrip.getInputNetworkGeometryFile()); assertNull(defaultRoundTrip.getOutputPreparedNetworkFile());
        assertNull(defaultRoundTrip.getOutputNetworkGeometryFile()); assertNull(defaultRoundTrip.getStopMatchingReportFile());
        var config = config(); config.setOutputNetworkGeometryFile("geometry-out.csv"); config.setOutputPreparedNetworkFile("prepared.xml.gz");
        config.writeToFile(temporary.resolve("config.xml").toString());
        var loaded = PublicTransitMappingConfigGroup.loadConfig(temporary.resolve("config.xml").toString());
        assertTrue(loaded.getSplitLinksAtStops()); assertEquals(Set.of("bus"), loaded.getStopLinkSplittingModes());
        assertEquals(config.getInputNetworkGeometryFile(), loaded.getInputNetworkGeometryFile());
        assertEquals("prepared.xml.gz", loaded.getOutputPreparedNetworkFile());
    }
}
