package org.matsim.pt2matsim.osm;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.lanes.*;
import org.matsim.pt2matsim.config.OsmConverterConfigGroup;
import org.matsim.pt2matsim.osm.lib.*;
import static org.junit.jupiter.api.Assertions.*;

class OsmNetworkWithLanesConverterTest {
    @TempDir Path temp;

    private static final String NODES = """
        <node id="1" lon="0" lat="-0.001"/><node id="2" lon="0" lat="0"/>
        <node id="3" lon="-0.001" lat="0"/><node id="4" lon="0" lat="0.001"/>
        <node id="5" lon="0.001" lat="0"/><node id="6" lon="0" lat="0.002"/>
        <node id="7" lon="0.001" lat="0.001"/>
        """;

    private static String way(int id, String nodes, String tags) {
        StringBuilder text = new StringBuilder("<way id=\"" + id + "\">");
        for (String node : nodes.split(",")) text.append("<nd ref=\"").append(node).append("\"/>");
        return text + "<tag k=\"highway\" v=\"primary\"/><tag k=\"oneway\" v=\"yes\"/>" + tags + "</way>";
    }

    private static String tag(String key, String value) { return "<tag k=\"" + key + "\" v=\"" + value + "\"/>"; }

    private static String restriction(String value, String extra, boolean viaWay) {
        return "<relation id=\"100\"><member type=\"way\" ref=\"10\" role=\"from\"/>"
                + (viaWay ? "<member type=\"way\" ref=\"30\" role=\"via\"/><member type=\"way\" ref=\"50\" role=\"to\"/>"
                : "<member type=\"node\" ref=\"2\" role=\"via\"/><member type=\"way\" ref=\"20\" role=\"to\"/>")
                + tag("type", "restriction") + tag("restriction", value) + extra + "</relation>";
    }

    private OsmNetworkWithLanesConverter convert(String incomingTags, String relation) throws Exception {
        return convert(incomingTags, relation, false);
    }

    private OsmNetworkWithLanesConverter convert(String incomingTags, String relation, boolean viaWay) throws Exception {
        return convert(incomingTags, relation, viaWay, Set.of("car", "bus"));
    }

    private OsmNetworkWithLanesConverter convert(String incomingTags, String relation, boolean viaWay, Set<String> modes) throws Exception {
        Path file = temp.resolve("input.osm");
        Files.writeString(file, "<osm version=\"0.6\">" + NODES
                + way(10, "1,2", incomingTags) + way(20, "2,3", "")
                + way(30, viaWay ? "2,4,6" : "2,4", "") + way(40, "2,5", "")
                + (viaWay ? way(50, "6,7", "") + way(60, "4,7", "") : "") + relation + "</osm>");
        return convertFile(file, modes);
    }

    private OsmNetworkWithLanesConverter convertFile(Path file, Set<String> modes) {
        OsmData data = new OsmLaneData();
        new OsmFileReader(data).readFile(file.toString());
        OsmConverterConfigGroup config = new OsmConverterConfigGroup();
        config.addParameterSet(new OsmConverterConfigGroup.OsmWayParams("highway", "primary", 1, 10, 1, 1500, true, modes));
        config.setOutputCoordinateSystem("EPSG:3857");
        config.setKeepPaths(true);
        config.setRespectVehicleAccess(true);
        OsmNetworkWithLanesConverter converter = new OsmNetworkWithLanesConverter(data);
        converter.convert(config);
        return converter;
    }

    private static Link link(OsmNetworkWithLanesConverter c, int way, boolean dedicated) {
        return c.getNetwork().getLinks().values().stream().filter(l -> c.osmIds.get(l.getId()).toString().equals("" + way))
                .filter(l -> l.getId().toString().endsWith("_spec") == dedicated).findFirst().orElseThrow();
    }

    private static Lane lane(OsmNetworkWithLanesConverter c, Link link, int index) {
        return c.getLanes().getLanesToLinkAssignments().get(link.getId()).getLanes().get(Id.create(link.getId() + "." + index, Lane.class));
    }

    @Test void turnLanesAndXmlRoundTrip() throws Exception {
        var c = convert(tag("lanes", "3") + tag("turn:lanes", "left|through|right"), "");
        Link from = link(c, 10, false);
        assertEquals(List.of(link(c, 20, false).getId()), lane(c, from, 1).getToLinkIds());
        assertEquals(List.of(link(c, 30, false).getId()), lane(c, from, 2).getToLinkIds());
        assertEquals(List.of(link(c, 40, false).getId()), lane(c, from, 3).getToLinkIds());
        assertEquals(-2, lane(c, from, 1).getAlignment());
        assertEquals(0, lane(c, from, 2).getAlignment());
        assertEquals(2, lane(c, from, 3).getAlignment());
        double total = c.getLanes().getLanesToLinkAssignments().get(from.getId()).getLanes().values().stream()
                .filter(l -> l.getToLinkIds() != null).mapToDouble(Lane::getCapacityVehiclesPerHour).sum();
        assertEquals(from.getCapacity(), total, 1e-6);
        Path xml = temp.resolve("lanes.xml");
        new LanesWriter(c.getLanes()).write(xml.toString());
        var scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        new LanesReader(scenario).readFile(xml.toString());
        assertEquals(c.getLanes().getLanesToLinkAssignments().size(), scenario.getLanes().getLanesToLinkAssignments().size());
        var restored = scenario.getLanes().getLanesToLinkAssignments().get(from.getId()).getLanes();
        assertEquals(-2, restored.get(Id.create(from.getId() + ".1", Lane.class)).getAlignment());
        assertEquals(2, restored.get(Id.create(from.getId() + ".3", Lane.class)).getAlignment());
        c.getReport().write(temp.resolve("report.csv"));
        assertTrue(Files.readString(temp.resolve("report.csv")).contains("linksWithLanes"));
    }

    @Test void noTurnAndBusException() throws Exception {
        var c = convert("", restriction("no_left_turn", tag("except", "bus"), false));
        Link from = link(c, 10, false), left = link(c, 20, false);
        var dnl = NetworkUtils.getDisallowedNextLinks(from);
        assertEquals(List.of(List.of(left.getId())), dnl.getDisallowedLinkSequences("car"));
        assertTrue(dnl.getDisallowedLinkSequences("bus").isEmpty());
        assertTrue(lane(c, from, 1).getToLinkIds().contains(left.getId()), "Shared lanes retain the exempt bus movement");
    }

    @Test void modeSpecificOverride() throws Exception {
        var c = convert("", restriction("no_left_turn", tag("restriction:bus", "only_left_turn"), false));
        Link from = link(c, 10, false);
        var dnl = NetworkUtils.getDisallowedNextLinks(from);
        assertEquals(List.of(List.of(link(c, 20, false).getId())), dnl.getDisallowedLinkSequences("car"));
        assertTrue(dnl.getDisallowedLinkSequences("bus").contains(List.of(link(c, 30, false).getId())));
        assertFalse(dnl.getDisallowedLinkSequences("bus").contains(List.of(link(c, 20, false).getId())));
    }

    @Test void onlyTurnFiltersLanesForAllModes() throws Exception {
        var c = convert("", restriction("only_left_turn", "", false));
        assertEquals(List.of(link(c, 20, false).getId()), lane(c, link(c, 10, false), 1).getToLinkIds());
    }

    @Test void commonModeAliasesAndWalkingException() throws Exception {
        var c = convert("", restriction("no_left_turn", tag("except", "bicycle"), false), false,
                Set.of("car", "bus", "taxi", "bike", "truck", "motorcycle", "walk"));
        Link from = link(c, 10, false), left = link(c, 20, false);
        var dnl = NetworkUtils.getDisallowedNextLinks(from);
        for (String mode : List.of("car", "bus", "taxi", "truck", "motorcycle")) {
            assertTrue(dnl.getDisallowedLinkSequences(mode).contains(List.of(left.getId())), mode);
        }
        assertTrue(dnl.getDisallowedLinkSequences("bike").isEmpty());
        assertTrue(dnl.getDisallowedLinkSequences("walk").isEmpty());
    }

    @Test void originalRestrictionFixturesWithDefaultCleaning() {
        for (String file : List.of("tr0_valid.osm", "tr2_valid.osm", "tr3_valid.osm", "tr4_valid.osm", "tr5_valid.osm", "tr6_valid.osm")) {
            OsmData data = new OsmLaneData();
            new OsmFileReader(data).readFile("test/osm/" + file);
            var config = OsmConverterConfigGroup.createDefaultConfig();
            config.setOutputCoordinateSystem("EPSG:25832");
            var converter = new OsmNetworkWithLanesConverter(data);
            converter.convert(config);
            assertNotNull(converter.getLanes());
            assertTrue(org.matsim.core.network.turnRestrictions.DisallowedNextLinksUtils.isValid(converter.getNetwork()), file);
        }
    }

    @Test void derivedCarModesInheritMotorcarRestrictions() throws Exception {
        Path file = temp.resolve("derived-mode.osm");
        Files.writeString(file, "<osm version=\"0.6\">" + NODES + way(10, "1,2", tag("oneway", "no"))
                + way(20, "2,3", tag("oneway", "no")) + way(30, "2,4", tag("oneway", "no"))
                + way(40, "2,5", tag("oneway", "no"))
                + restriction("no_left_turn", "", false).replace("k=\"restriction\"", "k=\"restriction:motorcar\"") + "</osm>");
        OsmData data = new OsmLaneData();
        new OsmFileReader(data).readFile(file.toString());
        var config = OsmConverterConfigGroup.createDefaultConfig();
        config.setOutputCoordinateSystem("EPSG:3857");
        config.addParameterSet(new OsmConverterConfigGroup.RoutableSubnetworkParams("car_passenger", Set.of("car")));
        var converter = new OsmNetworkWithLanesConverter(data);
        converter.convert(config);
        Link from = converter.getNetwork().getLinks().values().stream().filter(l -> l.getFromNode().getId().toString().equals("1")
                && l.getToNode().getId().toString().equals("2")).findFirst().orElseThrow();
        Link left = converter.getNetwork().getLinks().values().stream().filter(l -> l.getFromNode().getId().toString().equals("2")
                && l.getToNode().getId().toString().equals("3")).findFirst().orElseThrow();
        assertTrue(from.getAllowedModes().contains("car_passenger"));
        assertTrue(NetworkUtils.getDisallowedNextLinks(from).getDisallowedLinkSequences("car_passenger").contains(List.of(left.getId())));
    }

    @Test void onlyViaWayConstrainsIntermediateExit() throws Exception {
        var c = convert("", restriction("only_straight_on", "", true), true);
        Link from = link(c, 10, false);
        Link firstVia = c.getNetwork().getLinks().values().stream().filter(l -> c.osmIds.get(l.getId()).toString().equals("30")
                && l.getFromNode().getId().toString().equals("2")).findFirst().orElseThrow();
        var dnl = NetworkUtils.getDisallowedNextLinks(from);
        assertTrue(dnl.getDisallowedLinkSequences("car").contains(List.of(firstVia.getId(), link(c, 60, false).getId())));
        assertTrue(dnl.getDisallowedLinkSequences("car").contains(List.of(link(c, 20, false).getId())));
    }

    @Test void reservedBusLaneUsesCorrectTurnTag() throws Exception {
        var c = convert(tag("lanes", "3") + tag("turn:lanes", "left|through|right")
                + tag("bus:lanes", "yes|yes|designated"), "");
        Link main = link(c, 10, false), bus = link(c, 10, true);
        assertEquals(2, c.getLanes().getLanesToLinkAssignments().get(main.getId()).getLanes().size() - 1);
        assertEquals(List.of(link(c, 40, false).getId()), lane(c, bus, 1).getToLinkIds());
        assertEquals(3, lane(c, bus, 1).getAttributes().getAttribute("osmLaneIndex"));
    }

    @Test void missingTagsAreCounted() throws Exception {
        var c = convert(tag("lanes", "3"), "");
        assertEquals(3L, c.getReport().getCounts().get("inferredLaneMovements"));
        assertEquals(1L, c.getReport().getCounts().get("linksWithInferredConnections"));
    }

    @Test void laneTopologyIsAlsoRespectedByRouting() throws Exception {
        var c = convert(tag("lanes", "1") + tag("turn:lanes", "through"), "");
        Link from = link(c, 10, false);
        var dnl = NetworkUtils.getDisallowedNextLinks(from);
        assertTrue(dnl.getDisallowedLinkSequences("car").contains(List.of(link(c, 20, false).getId())));
        assertTrue(dnl.getDisallowedLinkSequences("bus").contains(List.of(link(c, 40, false).getId())));
        assertEquals(2L, c.getReport().getCounts().get("unservedLegalMovements"));
    }

    @Test void contradictoryTurnTagsDoNotReopenForbiddenMovements() throws Exception {
        var c = convert(tag("lanes", "1") + tag("turn:lanes", "left"), restriction("no_left_turn", "", false));
        assertEquals(1L, c.getReport().getCounts().get("conflictingTurnIndications"));
        assertFalse(lane(c, link(c, 10, false), 1).getToLinkIds().contains(link(c, 20, false).getId()));
        assertTrue(NetworkUtils.getDisallowedNextLinks(link(c, 10, false)).getDisallowedLinkSequences("car")
                .contains(List.of(link(c, 20, false).getId())));
    }

    @Test void reservedLanePositionsWithoutTurnTagsArePreserved() throws Exception {
        var c = convert(tag("lanes", "3") + tag("bus:lanes", "yes|yes|designated"), "");
        Link bus = link(c, 10, true);
        assertEquals(3, lane(c, bus, 1).getAttributes().getAttribute("osmLaneIndex"));
        assertEquals(0L, c.getReport().getCounts().get("inferredReservedLanePositions"));
    }

    @Test void missingMandatoryDestinationIsSkippedAndReported() throws Exception {
        Path file = temp.resolve("truncated.osm");
        Files.writeString(file, "<osm version=\"0.6\">" + NODES + way(10, "1,2", "")
                + restriction("only_left_turn", "", false) + "</osm>");
        var data = new OsmLaneData();
        new OsmFileReader(data).readFile(file.toString());
        assertEquals(1L, data.getReport().getCounts().get("missingDestinationMembers"));
        assertEquals(1L, data.getReport().getCounts().get("relationsSkippedMissingDestination"));
        assertFalse(data.getRelations().containsKey(Id.create(100, Osm.Relation.class)));
    }

    @Test void readerDiscardsUnusedNodesButPreservesRelationMembers() throws Exception {
        Path file = temp.resolve("unused-nodes.osm");
        Files.writeString(file, "<osm version=\"0.6\">" + NODES + way(10, "1,2", "")
                + "<relation id=\"300\"><member type=\"node\" ref=\"7\" role=\"stop\"/>"
                + tag("type", "route") + tag("route", "bus") + "</relation></osm>");
        var data = new OsmLaneData();
        new OsmFileReader(data).readFile(file.toString());
        assertEquals(3, data.getNodes().size());
        assertTrue(data.getNodes().containsKey(Id.create(7, Osm.Node.class)));
        assertFalse(data.getNodes().containsKey(Id.create(6, Osm.Node.class)));
        assertEquals(1, data.getRelations().get(Id.create(300, Osm.Relation.class)).getMembers().size());
    }

    @Test void missingApproachIsReportedWithoutGuessingAnotherRoad() throws Exception {
        Path file = temp.resolve("missing-approach.osm");
        Files.writeString(file, "<osm version=\"0.6\">" + NODES + way(40, "5,2", "") + way(20, "2,3", "")
                + restriction("no_left_turn", "", false) + "</osm>");
        var c = convertFile(file, Set.of("car", "bus"));
        assertEquals(1L, c.getReport().getCounts().get("missingFromWays"));
        assertEquals(1L, c.getReport().getCounts().get("relationsWithoutRepresentedApproach"));
        assertEquals(0L, c.getReport().getCounts().get("restrictionRelationsApplied"));
        assertNull(NetworkUtils.getDisallowedNextLinks(link(c, 40, false)));
        assertEquals(List.of(link(c, 20, false).getId()), lane(c, link(c, 40, false), 1).getToLinkIds());
        assertFalse(c.osmData.getRelations().containsKey(Id.create(100, Osm.Relation.class)));
        assertFalse(c.osmData.getNodes().get(Id.create(2, Osm.Node.class)).getRelations().containsKey(Id.create(100, Osm.Relation.class)));
    }

    @Test void absentIncomingMemberIsSkippedAndReported() throws Exception {
        var c = convert("", restriction("no_left_turn", "", false)
                .replace("<member type=\"way\" ref=\"10\" role=\"from\"/>", ""));
        assertEquals(1L, c.getReport().getCounts().get("relationsWithoutRepresentedApproach"));
        assertEquals(0L, c.getReport().getCounts().get("missingFromWays"));
        assertNull(NetworkUtils.getDisallowedNextLinks(link(c, 10, false)));
        assertFalse(c.osmData.getRelations().containsKey(Id.create(100, Osm.Relation.class)));
    }

    @Test void absentDestinationMemberIsSkippedAndReported() throws Exception {
        var c = convert("", restriction("only_left_turn", "", false)
                .replace("<member type=\"way\" ref=\"20\" role=\"to\"/>", ""));
        assertEquals(1L, c.getReport().getCounts().get("relationsSkippedMissingDestination"));
        assertEquals(0L, c.getReport().getCounts().get("missingDestinationMembers"));
        assertNull(NetworkUtils.getDisallowedNextLinks(link(c, 10, false)));
        assertFalse(c.osmData.getRelations().containsKey(Id.create(100, Osm.Relation.class)));
    }

    @Test void multiFromNoEntryRetainsRestrictionOnAvailableApproach() throws Exception {
        String relation = restriction("no_entry", "", false).replace("<relation id=\"100\">",
                "<relation id=\"100\"><member type=\"way\" ref=\"999\" role=\"from\"/>");
        var c = convert("", relation);
        assertEquals(1L, c.getReport().getCounts().get("missingFromWays"));
        assertEquals(0L, c.getReport().getCounts().get("relationsWithoutRepresentedApproach"));
        assertTrue(NetworkUtils.getDisallowedNextLinks(link(c, 10, false)).getDisallowedLinkSequences("car")
                .contains(List.of(link(c, 20, false).getId())));
    }

    @Test void missingProhibitedDestinationIsReportedAndSkipped() throws Exception {
        Path file = temp.resolve("missing-destination.osm");
        Files.writeString(file, "<osm version=\"0.6\">" + NODES + way(10, "1,2", "") + way(30, "2,4", "")
                + restriction("no_left_turn", "", false) + "</osm>");
        var c = convertFile(file, Set.of("car", "bus"));
        assertEquals(1L, c.getReport().getCounts().get("unavailableProhibitedPathMembers"));
        assertEquals(1L, c.getReport().getCounts().get("restrictionsWithoutRepresentedProhibitedPath"));
        assertNull(NetworkUtils.getDisallowedNextLinks(link(c, 10, false)));
        assertEquals(List.of(link(c, 30, false).getId()), lane(c, link(c, 10, false), 1).getToLinkIds());
        assertFalse(c.osmData.getNodes().get(Id.create(2, Osm.Node.class)).getRelations().containsKey(Id.create(100, Osm.Relation.class)));
    }

    @Test void missingProhibitedViaWayIsReportedAndSkipped() throws Exception {
        Path file = temp.resolve("missing-via.osm");
        Files.writeString(file, "<osm version=\"0.6\">" + NODES + way(10, "1,2", "") + way(50, "2,4", "")
                + restriction("no_straight_on", "", true) + "</osm>");
        var c = convertFile(file, Set.of("car", "bus"));
        assertEquals(1L, c.getReport().getCounts().get("restrictionsWithoutRepresentedProhibitedPath"));
        assertNull(NetworkUtils.getDisallowedNextLinks(link(c, 10, false)));
    }

    @Test void multiTargetNoExitRetainsAvailableProhibitions() throws Exception {
        String relation = restriction("no_exit", "", false).replace("<relation id=\"100\">",
                "<relation id=\"100\"><member type=\"way\" ref=\"999\" role=\"to\"/>");
        var c = convert("", relation);
        assertEquals(1L, c.getReport().getCounts().get("partiallyRepresentedRestrictionRelations"));
        assertEquals(0L, c.getReport().getCounts().get("restrictionsWithoutRepresentedProhibitedPath"));
        assertTrue(NetworkUtils.getDisallowedNextLinks(link(c, 10, false)).getDisallowedLinkSequences("car")
                .contains(List.of(link(c, 20, false).getId())));
    }

    @Test void missingDestinationWithMandatoryModeOverrideIsSkipped() throws Exception {
        Path file = temp.resolve("missing-bus-only-destination.osm");
        Files.writeString(file, "<osm version=\"0.6\">" + NODES + way(10, "1,2", "") + way(30, "2,4", "")
                + restriction("no_left_turn", tag("restriction:bus", "only_left_turn"), false) + "</osm>");
        var c = convertFile(file, Set.of("car", "bus"));
        assertEquals(1L, c.getReport().getCounts().get("relationsSkippedMissingDestination"));
        assertNull(NetworkUtils.getDisallowedNextLinks(link(c, 10, false)));
        assertEquals(List.of(link(c, 30, false).getId()), lane(c, link(c, 10, false), 1).getToLinkIds());
    }

    @Test void missingViaIsInferredFromUniqueSharedJunction() throws Exception {
        var c = convert("", restriction("no_left_turn", "", false)
                .replace("<member type=\"node\" ref=\"2\" role=\"via\"/>", ""));
        assertEquals(1L, c.getReport().getCounts().get("inferredRestrictionJunctions"));
        assertTrue(NetworkUtils.getDisallowedNextLinks(link(c, 10, false)).getDisallowedLinkSequences("car")
                .contains(List.of(link(c, 20, false).getId())));
    }

    @Test void missingViaWithMultipleSharedNodesIsSkippedAndReported() throws Exception {
        Path file = temp.resolve("ambiguous-junction.osm");
        Files.writeString(file, "<osm version=\"0.6\">" + NODES + way(10, "1,2", "") + way(20, "2,1", "")
                + restriction("no_left_turn", "", false)
                    .replace("<member type=\"node\" ref=\"2\" role=\"via\"/>", "") + "</osm>");
        var c = convertFile(file, Set.of("car"));
        assertEquals(1L, c.getReport().getCounts().get("relationsSkippedUnresolvedJunction"));
        assertEquals(0L, c.getReport().getCounts().get("inferredRestrictionJunctions"));
        assertNull(NetworkUtils.getDisallowedNextLinks(link(c, 10, false)));
        assertTrue(c.getReport().getEntries().stream().anyMatch(e -> e.category().equals("relationsSkippedUnresolvedJunction")
                && e.osmId().equals("100") && e.detail().contains("2 shared nodes")));
    }

    @Test void missingViaWithNoSharedNodesIsSkippedAndReported() throws Exception {
        Path file = temp.resolve("disconnected-junction.osm");
        Files.writeString(file, "<osm version=\"0.6\">" + NODES + way(10, "1,2", "") + way(20, "3,4", "")
                + restriction("no_left_turn", "", false)
                    .replace("<member type=\"node\" ref=\"2\" role=\"via\"/>", "") + "</osm>");
        var c = convertFile(file, Set.of("car"));
        assertEquals(1L, c.getReport().getCounts().get("relationsSkippedUnresolvedJunction"));
        assertNull(NetworkUtils.getDisallowedNextLinks(link(c, 10, false)));
        assertTrue(c.getReport().getEntries().stream().anyMatch(e -> e.category().equals("relationsSkippedUnresolvedJunction")
                && e.detail().contains("0 shared nodes")));
    }

    @Test void conflictingViaNodesAreSkippedAndReported() throws Exception {
        var c = convert("", restriction("only_left_turn", "", false).replace("<relation id=\"100\">",
                "<relation id=\"100\"><member type=\"node\" ref=\"3\" role=\"via\"/>"));
        assertEquals(1L, c.getReport().getCounts().get("relationsSkippedUnresolvedJunction"));
        assertNull(NetworkUtils.getDisallowedNextLinks(link(c, 10, false)));
    }

    @Test void unavailableViaNodeIsSkippedAndReported() throws Exception {
        var c = convert("", restriction("only_left_turn", "", false)
                .replace("ref=\"2\" role=\"via\"", "ref=\"999\" role=\"via\""));
        assertEquals(1L, c.getReport().getCounts().get("relationsSkippedUnresolvedJunction"));
        assertFalse(c.osmData.getRelations().containsKey(Id.create(100, Osm.Relation.class)));
        assertNull(NetworkUtils.getDisallowedNextLinks(link(c, 10, false)));
    }

    @Test void soleForbiddenExitIsNeverReopened() throws Exception {
        Path file = temp.resolve("sole-exit.osm");
        Files.writeString(file, "<osm version=\"0.6\">" + NODES + way(10, "1,2", "") + way(20, "2,3", "")
                + restriction("no_left_turn", "", false) + "</osm>");
        var c = convertFile(file, Set.of("car", "bus"));
        Link from = link(c, 10, false), left = link(c, 20, false);
        assertEquals(List.of(List.of(left.getId())), NetworkUtils.getDisallowedNextLinks(from).getDisallowedLinkSequences("car"));
        assertFalse(c.getLanes().getLanesToLinkAssignments().containsKey(from.getId()));
        assertEquals(2L, c.getReport().getCounts().get("deadEndLinks"));
    }

    @Test void reverseOnewayReadsUndirectedTurnTags() throws Exception {
        Path file = temp.resolve("reverse-oneway.osm");
        Files.writeString(file, "<osm version=\"0.6\">" + NODES + way(10, "2,1", tag("oneway", "-1") + tag("turn:lanes", "left"))
                + way(20, "2,3", "") + way(30, "2,4", "") + way(40, "2,5", "") + "</osm>");
        var c = convertFile(file, Set.of("car", "bus"));
        assertEquals(List.of(link(c, 20, false).getId()), lane(c, link(c, 10, false), 1).getToLinkIds());
    }

    @Test void runnerWritesFailureReportWithoutNetworkOutput() throws Exception {
        Path file = temp.resolve("bad-input.osm"), network = temp.resolve("network.xml"), report = temp.resolve("failure.csv");
        Files.writeString(file, "<osm version=\"0.6\"><way");
        var config = OsmConverterConfigGroup.createDefaultConfig();
        config.setOsmFile(file.toString());
        config.setOutputNetworkFile(network.toString());
        assertThrows(java.io.UncheckedIOException.class, () -> org.matsim.pt2matsim.run.Osm2NetworkWithLanes.run(config,
                temp.resolve("lanes.xml").toString(), report));
        assertFalse(Files.exists(network));
        assertTrue(Files.readString(report).contains("conversionErrors"));
    }

    @Test void conditionalRestrictionsAreAuditedWithoutStoppingConversion() throws Exception {
        var c = convert("", restriction("no_left_turn", tag("restriction:conditional", "no_left_turn @ (Mo-Fr)"), false));
        assertEquals(1L, c.getReport().getCounts().get("relationsSkippedUnsupported"));
        assertNull(NetworkUtils.getDisallowedNextLinks(link(c, 10, false)));
        assertTrue(c.getReport().getEntries().stream().anyMatch(e -> e.osmId().equals("100")
                && e.detail().contains("Conditional") && e.detail().contains("members=")));
    }

    @Test void bicyclePriorityRulesAreReportedWithoutTurnBans() throws Exception {
        for (String value : List.of("give_way", "stop")) {
            var c = convert("", restriction(value, "", false)
                    .replace("k=\"restriction\"", "k=\"restriction:bicycle\""), false, Set.of("car", "bike"));
            assertEquals(1L, c.getReport().getCounts().get("priorityRulesNotSimulated"));
            assertEquals(0L, c.getReport().getCounts().get("restrictionRelationsApplied"));
            assertNull(NetworkUtils.getDisallowedNextLinks(link(c, 10, false)));
            assertTrue(lane(c, link(c, 10, false), 1).getToLinkIds().contains(link(c, 20, false).getId()));
            assertTrue(c.getReport().getEntries().stream().anyMatch(e -> e.category().equals("priorityRulesNotSimulated")
                    && e.osmId().equals("100") && e.detail().contains("restriction:bicycle=" + value)));
        }
    }

    @Test void bicyclePriorityOverridePreservesOtherModeRestrictions() throws Exception {
        var c = convert("", restriction("no_left_turn", tag("restriction:bicycle", "give_way"), false),
                false, Set.of("car", "bus", "bike"));
        var banned = NetworkUtils.getDisallowedNextLinks(link(c, 10, false));
        assertTrue(banned.getDisallowedLinkSequences("car").contains(List.of(link(c, 20, false).getId())));
        assertTrue(banned.getDisallowedLinkSequences("bus").contains(List.of(link(c, 20, false).getId())));
        assertTrue(banned.getDisallowedLinkSequences("bike").isEmpty());
        assertTrue(lane(c, link(c, 10, false), 1).getToLinkIds().contains(link(c, 20, false).getId()));
        assertEquals(1L, c.getReport().getCounts().get("priorityRulesNotSimulated"));
    }

    @Test void exactReceivingLaneConnectivityIsAuditedAndInferred() throws Exception {
        String relation = "<relation id=\"200\"><member type=\"way\" ref=\"10\" role=\"from\"/>"
                + "<member type=\"node\" ref=\"2\" role=\"via\"/><member type=\"way\" ref=\"20\" role=\"to\"/>"
                + tag("type", "connectivity") + tag("connectivity", "1:1") + "</relation>";
        var c = convert("", relation);
        assertEquals(1L, c.getReport().getCounts().get("relationsSkippedUnsupported"));
        assertTrue(lane(c, link(c, 10, false), 1).getToLinkIds().contains(link(c, 20, false).getId()));
        assertTrue(c.getReport().getEntries().stream().anyMatch(e -> e.osmId().equals("200")
                && e.detail().contains("receiving-lane") && e.detail().contains("1:1")));
    }

    @Test void unknownAndTimedRelationsDoNotDiscardSupportedRestrictions() throws Exception {
        String unknown = restriction("future_restriction", "", false);
        String timed = restriction("no_left_turn", tag("hour_on", "08:00"), false).replace("id=\"100\"", "id=\"101\"");
        String valid = restriction("no_left_turn", "", false).replace("id=\"100\"", "id=\"102\"");
        var c = convert("", unknown + timed + valid);
        assertEquals(2L, c.getReport().getCounts().get("relationsSkippedUnsupported"));
        assertEquals(1L, c.getReport().getCounts().get("restrictionRelationsApplied"));
        assertEquals(List.of(List.of(link(c, 20, false).getId())),
                NetworkUtils.getDisallowedNextLinks(link(c, 10, false)).getDisallowedLinkSequences("car"));
        assertEquals(2, c.getReport().getEntries().stream().filter(e -> e.category().equals("relationsSkippedUnsupported")).count());
    }

    @Test void unmatchedDirectedRestrictionIsAuditedOnce() throws Exception {
        Path file = temp.resolve("unmatched.osm");
        Files.writeString(file, "<osm version=\"0.6\">" + NODES + way(10, "1,2", "") + way(20, "3,2", "")
                + way(30, "2,4", "") + restriction("no_left_turn", "", false) + "</osm>");
        var c = convertFile(file, Set.of("car"));
        assertEquals(1L, c.getReport().getCounts().get("relationsSkippedUnsupported"));
        assertNull(NetworkUtils.getDisallowedNextLinks(link(c, 10, false)));
        assertTrue(lane(c, link(c, 10, false), 1).getToLinkIds().contains(link(c, 30, false).getId()));
    }

    @Test void failedRelationLeavesNoPartialBansAndPreservesOtherRelations() throws Exception {
        Path file = temp.resolve("cyclic-via.osm");
        Files.writeString(file, "<osm version=\"0.6\">" + NODES + way(10, "1,2", "") + way(20, "2,3", "")
                + way(30, "2,4,7,2,6", "") + way(50, "6,7", "")
                + restriction("only_straight_on", "", true)
                + restriction("no_left_turn", "", false).replace("id=\"100\"", "id=\"102\"") + "</osm>");
        var c = convertFile(file, Set.of("car"));
        assertEquals(1L, c.getReport().getCounts().get("relationsSkippedUnsupported"));
        assertEquals(List.of(List.of(link(c, 20, false).getId())),
                NetworkUtils.getDisallowedNextLinks(link(c, 10, false)).getDisallowedLinkSequences("car"));
        assertTrue(c.getReport().getEntries().stream().anyMatch(e -> e.category().equals("relationsSkippedUnsupported")
                && e.osmId().equals("100") && e.detail().contains("repeating-link")));
    }

    @Test void unknownNonNetworkRelationTypesAreRecorded() throws Exception {
        var c = convert("", "<relation id=\"999\"><member type=\"way\" ref=\"10\" role=\"member\"/>"
                + tag("type", "future_custom_type") + tag("custom", "value") + "</relation>");
        assertEquals(1L, c.getReport().getCounts().get("ignoredNonNetworkRelations"));
        assertNull(NetworkUtils.getDisallowedNextLinks(link(c, 10, false)));
        assertTrue(c.getReport().getEntries().stream().anyMatch(e -> e.osmId().equals("999")
                && e.detail().contains("future_custom_type") && e.detail().contains("value")));
    }

    @Test void zeroGeneralTrafficLanesAreRemovedWhileReservedBusLinkSurvives() throws Exception {
        var c = convert(tag("lanes", "1") + tag("bus:lanes", "designated"), "");
        assertEquals(1L, c.getReport().getCounts().get("unusableNetworkLinksRemoved"));
        assertEquals(1, link(c, 10, true).getNumberOfLanes());
        assertTrue(c.getNetwork().getLinks().values().stream().noneMatch(l -> c.osmIds.get(l.getId()).toString().equals("10")
                && !l.getId().toString().endsWith("_spec")));
        assertTrue(c.getReport().getEntries().stream().anyMatch(e -> e.category().equals("unusableNetworkLinksRemoved")
                && e.osmId().equals("10") && e.detail().contains("lanes=0.0")));
    }

    @Test void bicycleConditionalDoesNotDiscardCarOrBusBan() throws Exception {
        var c = convert("", restriction("no_left_turn", tag("bicycle:conditional", "yes @ (21:00-00:00)"), false),
                false, Set.of("car", "bus", "bike"));
        var dnl = NetworkUtils.getDisallowedNextLinks(link(c, 10, false));
        for (String mode : List.of("car", "bus")) assertTrue(dnl.getDisallowedLinkSequences(mode).contains(List.of(link(c, 20, false).getId())));
        assertTrue(dnl.getDisallowedLinkSequences("bike").isEmpty());
        assertEquals(1L, c.getReport().getCounts().get("restrictionRelationsPartiallyApplied"));
        assertEquals(1L, c.getReport().getCounts().get("conditionalTagsNotSimulated"));
    }

    @Test void hgvConditionalDoesNotDiscardGeneralCarOnlyTurn() throws Exception {
        var c = convert("", restriction("only_left_turn", tag("restriction:hgv:conditional", "none @ (Mo-Sa 05:00-11:00)")
                + tag("except", "bicycle;bus"), false), false, Set.of("car", "bus", "truck"));
        var dnl = NetworkUtils.getDisallowedNextLinks(link(c, 10, false));
        assertTrue(dnl.getDisallowedLinkSequences("car").contains(List.of(link(c, 30, false).getId())));
        assertTrue(dnl.getDisallowedLinkSequences("truck").isEmpty());
        assertTrue(dnl.getDisallowedLinkSequences("bus").isEmpty());
        assertEquals(1L, c.getReport().getCounts().get("restrictionModesSkippedUnsupported"));
    }

    @Test void genericConditionalPreservesUnconditionalBusOverride() throws Exception {
        var c = convert("", restriction("no_left_turn", tag("restriction:conditional", "no_straight_on @ (Mo-Fr)")
                + tag("restriction:bus", "no_left_turn"), false));
        var dnl = NetworkUtils.getDisallowedNextLinks(link(c, 10, false));
        assertTrue(dnl.getDisallowedLinkSequences("car").isEmpty());
        assertTrue(dnl.getDisallowedLinkSequences("bus").contains(List.of(link(c, 20, false).getId())));
        assertEquals(1L, c.getReport().getCounts().get("restrictionRelationsPartiallyApplied"));
    }

    @Test void unavailableCarTargetDoesNotDiscardBusOnlyTurn() throws Exception {
        Path file = temp.resolve("bus-only-target.osm");
        Files.writeString(file, "<osm version=\"0.6\">" + NODES + way(10, "1,2", "")
                + way(20, "2,3", tag("motorcar", "no")) + way(30, "2,4", "") + way(40, "2,5", "")
                + restriction("only_left_turn", "", false) + "</osm>");
        var c = convertFile(file, Set.of("car", "bus"));
        assertFalse(link(c, 20, false).getAllowedModes().contains("car"));
        var dnl = NetworkUtils.getDisallowedNextLinks(link(c, 10, false));
        assertTrue(dnl.getDisallowedLinkSequences("bus").contains(List.of(link(c, 30, false).getId())));
        assertTrue(dnl.getDisallowedLinkSequences("car").isEmpty());
        assertEquals(1L, c.getReport().getCounts().get("restrictionRelationsApplied"));
        assertEquals(1L, c.getReport().getCounts().get("restrictionRelationsPartiallyApplied"));
        assertEquals(0L, c.getReport().getCounts().get("relationsSkippedUnsupported"));
    }

    @Test void legacyHgvTypeDoesNotBecomeAGeneralCarBan() throws Exception {
        var c = convert("", restriction("no_left_turn", "", false).replace("v=\"restriction\"", "v=\"restriction:hgv\""),
                false, Set.of("car", "bus", "truck"));
        var dnl = NetworkUtils.getDisallowedNextLinks(link(c, 10, false));
        assertTrue(dnl.getDisallowedLinkSequences("truck").contains(List.of(link(c, 20, false).getId())));
        assertTrue(dnl.getDisallowedLinkSequences("car").isEmpty());
        assertTrue(dnl.getDisallowedLinkSequences("bus").isEmpty());
        assertEquals(1L, c.getReport().getCounts().get("normalizedRestrictionTags"));
    }

    @Test void psvAndBicycleNoOverridesPreserveGeneralCarBan() throws Exception {
        var c = convert("", restriction("no_left_turn", tag("restriction:psv", "no") + tag("restriction:bicycle", "no")
                + tag("except", "bus"), false), false, Set.of("car", "bus", "taxi", "bike"));
        var dnl = NetworkUtils.getDisallowedNextLinks(link(c, 10, false));
        assertTrue(dnl.getDisallowedLinkSequences("car").contains(List.of(link(c, 20, false).getId())));
        for (String mode : List.of("bus", "taxi", "bike")) assertTrue(dnl.getDisallowedLinkSequences(mode).isEmpty());
        assertEquals(2L, c.getReport().getCounts().get("normalizedRestrictionTags"));
    }

    @Test void unknownBusValueDoesNotDiscardKnownCarRule() throws Exception {
        var c = convert("", restriction("no_left_turn", tag("restriction:bus", "future_value"), false));
        var dnl = NetworkUtils.getDisallowedNextLinks(link(c, 10, false));
        assertTrue(dnl.getDisallowedLinkSequences("car").contains(List.of(link(c, 20, false).getId())));
        assertTrue(dnl.getDisallowedLinkSequences("bus").isEmpty());
        assertEquals(1L, c.getReport().getCounts().get("restrictionRelationsPartiallyApplied"));
    }

    @Test void unknownGenericValueDoesNotDiscardKnownBusRule() throws Exception {
        var c = convert("", restriction("future_value", tag("restriction:bus", "no_left_turn"), false));
        var dnl = NetworkUtils.getDisallowedNextLinks(link(c, 10, false));
        assertTrue(dnl.getDisallowedLinkSequences("bus").contains(List.of(link(c, 20, false).getId())));
        assertTrue(dnl.getDisallowedLinkSequences("car").isEmpty());
        assertEquals(1L, c.getReport().getCounts().get("restrictionRelationsPartiallyApplied"));
    }

    @Test void multipleOnlyTargetsAreAuditedAsAllowedAlternatives() throws Exception {
        var c = convert("", restriction("only_straight_on", "", false).replace("<relation id=\"100\">",
                "<relation id=\"100\"><member type=\"way\" ref=\"30\" role=\"to\"/>"));
        var dnl = NetworkUtils.getDisallowedNextLinks(link(c, 10, false));
        assertEquals(List.of(List.of(link(c, 40, false).getId())), dnl.getDisallowedLinkSequences("car"));
        assertEquals(1L, c.getReport().getCounts().get("inferredRestrictionCardinalities"));
    }

    @Test void multiApproachNoLeftTurnIsAuditedAndAppliedToEachApproach() throws Exception {
        Path file = temp.resolve("multiple-approaches.osm");
        Files.writeString(file, "<osm version=\"0.6\">" + NODES + way(10, "1,2", "") + way(40, "5,2", "")
                + way(20, "2,3", "") + way(30, "2,4", "")
                + restriction("no_left_turn", "", false).replace("<relation id=\"100\">",
                    "<relation id=\"100\"><member type=\"way\" ref=\"40\" role=\"from\"/>") + "</osm>");
        var c = convertFile(file, Set.of("car", "bus"));
        for (int way : List.of(10, 40)) assertTrue(NetworkUtils.getDisallowedNextLinks(link(c, way, false))
                .getDisallowedLinkSequences("car").contains(List.of(link(c, 20, false).getId())));
        assertEquals(1L, c.getReport().getCounts().get("inferredRestrictionCardinalities"));
    }
}
