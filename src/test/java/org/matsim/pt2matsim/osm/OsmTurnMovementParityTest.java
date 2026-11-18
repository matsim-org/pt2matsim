package org.matsim.pt2matsim.osm;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.lanes.Lane;
import org.matsim.core.network.NetworkUtils;
import org.matsim.pt2matsim.config.OsmConverterConfigGroup;
import org.matsim.pt2matsim.osm.lib.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.matsim.pt2matsim.osm.OsmAdditionalTurnInformationTest.link;
import static org.matsim.pt2matsim.osm.OsmAdditionalTurnInformationTest.permitted;

/** Compare every immediate mode-specific movement using the two converter entry points. */
class OsmTurnMovementParityTest {
    @TempDir Path temp;
    private static String tag(String key, String value) { return OsmAdditionalTurnInformationTest.tag(key, value); }

    private OsmMultimodalNetworkConverter convert(Path file, boolean lanes, boolean flag) {
        OsmData data = lanes ? new OsmLaneData() : new OsmDataImpl();
        new OsmFileReader(data).readFile(file.toString());
        var config = OsmAdditionalTurnInformationTest.config(flag);
        config.setRespectVehicleAccess(true);
        OsmMultimodalNetworkConverter converter = lanes ? new OsmNetworkWithLanesConverter(data) : new OsmMultimodalNetworkConverter(data);
        converter.convert(config);
        return converter;
    }
    private static String key(OsmMultimodalNetworkConverter c, Link link) {
        return c.osmIds.get(link.getId()) + ":" + link.getFromNode().getId() + ":" + link.getToNode().getId()
                + (link.getId().toString().endsWith("_spec") ? ":spec" : ":general");
    }
    private static Map<String, Boolean> movements(OsmMultimodalNetworkConverter c) {
        Map<String, Boolean> result = new TreeMap<>();
        for (Link from : c.getNetwork().getLinks().values()) for (Link out : from.getToNode().getOutLinks().values()) {
            var dnl = NetworkUtils.getDisallowedNextLinks(from);
            for (String mode : from.getAllowedModes()) if (out.getAllowedModes().contains(mode)) {
                result.put(key(c, from) + " -> " + key(c, out) + ":" + mode,
                        dnl == null || !dnl.getDisallowedLinkSequences(mode).contains(List.of(out.getId())));
            }
        }
        return result;
    }
    private void assertParity(Path file) {
        var normal = convert(file, false, true);
        var detailed = (OsmNetworkWithLanesConverter) convert(file, true, false);
        assertEquals(movements(detailed), movements(normal), file.toString());
        assertEquals(movements(detailed), movements(convert(file, true, true)), "Opt-in flag must not apply the inference twice in the lanes converter");
        for (String counter : List.of("inferredLaneMovements", "inferredUTurnModeBans", "unservedLegalMovements",
                "nonMotorLaneSlotsExcluded", "ambiguousTurnMatches", "linksWithInferredConnections")) {
            assertEquals(detailed.getReport().getCounts().get(counter), normal.getAdditionalTurnRestrictionCounts().get(counter), counter);
        }
    }
    @Test void convertersAgreeForArrowsFallbacksReservedLanesAndCyclingSlots() throws Exception {
        List<String> tags = List.of("", tag("turn:lanes", "left"), tag("turn:lanes", "unknown"),
                tag("oneway", "no"), tag("oneway", "no") + tag("turn:lanes:forward", "reverse"),
                tag("lanes", "3") + tag("turn:lanes", "left|through|right"),
                tag("lanes", "2") + tag("turn:lanes", "left|through|right") + tag("motor_vehicle:lanes", "yes|no|yes"),
                tag("lanes", "2") + tag("psv:lanes", "designated|yes") + tag("turn:lanes", "left|through"),
                tag("oneway", "no") + tag("lanes", "4") + tag("lanes:forward", "2") + tag("lanes:backward", "2")
                        + tag("turn:lanes:forward", "reverse|"));
        tags = new ArrayList<>(tags);
        tags.add(tag("oneway", "no") + tag("lanes", "3") + tag("lanes:backward", "1")
                + tag("lanes:psv:forward", "1") + tag("turn:lanes:forward", "left|through"));
        tags.add(tag("oneway", "no") + tag("lanes", "3") + tag("lanes:forward", "2") + tag("lanes:backward", "1")
                + tag("lanes:psv:forward", "1") + tag("psv:lanes:backward", "yes|designated")
                + tag("turn:lanes:forward", "left|through") + tag("turn:lanes:backward", "through"));
        int i = 0;
        for (String incoming : tags) {
            Path file = temp.resolve("case-" + i++ + ".osm");
            Files.writeString(file, """
                    <osm version="0.6">
                    <node id="1" lon="0" lat="-0.001"/><node id="2" lon="0" lat="0"/>
                    <node id="3" lon="-0.001" lat="0"/><node id="4" lon="0" lat="0.001"/>
                    <node id="5" lon="0.001" lat="0"/>
                    """ + OsmAdditionalTurnInformationTest.way(10, "1,2", incoming)
                    + OsmAdditionalTurnInformationTest.way(20, "2,3", "")
                    + OsmAdditionalTurnInformationTest.way(30, "2,4", "")
                    + OsmAdditionalTurnInformationTest.way(40, "2,5", "") + "</osm>");
            assertParity(file);
        }
    }
    // First OSM segments at the A1/A1a fork (node 352839257), in driving direction.
    private Path genevaFork(String arrows, String relation) throws Exception {
        Path file = temp.resolve("geneva-fork.osm");
        Files.writeString(file, """
                <osm version="0.6">
                <node id="894716517" lat="46.2561522" lon="6.1359909"/>
                <node id="352839257" lat="46.2558565" lon="6.1363532"/>
                <node id="894716527" lat="46.2554314" lon="6.1368282"/>
                <node id="2808699" lat="46.2554492" lon="6.1368807"/>
                """ + OsmAdditionalTurnInformationTest.way(486434950, "894716517,352839257",
                        tag("lanes", "3") + tag("turn:lanes", arrows))
                + OsmAdditionalTurnInformationTest.way(75795034, "352839257,894716527", tag("lanes", "2"))
                + OsmAdditionalTurnInformationTest.way(5196943, "352839257,2808699", tag("lanes", "1"))
                + relation + "</osm>");
        return file;
    }

    private void assertFork(Path file, boolean mainOpen, int... laneWays) {
        for (boolean withLanes : List.of(false, true)) {
            var converter = convert(file, withLanes, true);
            Link incoming = link(converter, 486434950, "894716517");
            Link main = link(converter, 75795034, "352839257");
            Link exit = link(converter, 5196943, "352839257");
            assertEquals(mainOpen, permitted(incoming, main, "car"));
            assertTrue(permitted(incoming, exit, "car"));
            var counts = converter instanceof OsmNetworkWithLanesConverter detailed
                    ? detailed.getReport().getCounts() : converter.getAdditionalTurnRestrictionCounts();
            assertEquals(mainOpen ? 1L : 0L, counts.get("forkLaneAssignments"));
            if (converter instanceof OsmNetworkWithLanesConverter detailed) {
                var lanes = detailed.getLanes().getLanesToLinkAssignments().get(incoming.getId()).getLanes();
                for (int i = 0; i < laneWays.length; i++) {
                    Link expected = link(converter, laneWays[i], "352839257");
                    assertEquals(List.of(expected.getId()), lanes.get(Id.create(incoming.getId() + "." + (i + 1), Lane.class)).getToLinkIds());
                }
            }
        }
    }

    @Test void genevaForkSeparatesTheLeftExitFromBothThroughLanes() throws Exception {
        assertFork(genevaFork("left|through|through", ""), true, 5196943, 75795034, 75795034);
    }

    @Test void mirroredForkSeparatesThroughLanesFromTheRightExit() throws Exception {
        Path file = genevaFork("through|through|right", "");
        Files.writeString(file, Files.readString(file).replace("lon=\"6.", "lon=\"-6."));
        assertFork(file, true, 75795034, 75795034, 5196943);
    }

    @Test void shallowForkRequiresTwoOrderedArrowGroupsAndRespectsSignedBans() throws Exception {
        assertFork(genevaFork("through|through|through", ""), true);
        assertFork(genevaFork("left;through|through|through", ""), true);
        // A shared lane retains both destinations; two later lanes are through-only.
        var detailed = (OsmNetworkWithLanesConverter) convert(genevaFork("left;through|through|through", ""), true, true);
        Link approach = link(detailed, 486434950, "894716517");
        var lanes = detailed.getLanes().getLanesToLinkAssignments().get(approach.getId()).getLanes();
        assertEquals(2, lanes.get(Id.create(approach.getId() + ".1", Lane.class)).getToLinkIds().size());
        assertEquals(List.of(link(detailed, 75795034, "352839257").getId()),
                lanes.get(Id.create(approach.getId() + ".2", Lane.class)).getToLinkIds());
        String ban = """
                <relation id="100"><member type="way" ref="486434950" role="from"/>
                <member type="node" ref="352839257" role="via"/><member type="way" ref="75795034" role="to"/>
                <tag k="type" v="restriction"/><tag k="restriction" v="no_straight_on"/></relation>
                """;
        assertFork(genevaFork("left|through|through", ban), false, 5196943, 5196943, 5196943);

        Path exempt = genevaFork("left|through|through", ban.replace("</relation>", tag("except", "bus") + "</relation>"));
        for (boolean withLanes : List.of(false, true)) {
            var converter = convert(exempt, withLanes, true);
            Link from = link(converter, 486434950, "894716517"), main = link(converter, 75795034, "352839257");
            assertFalse(permitted(from, main, "car"));
            assertTrue(permitted(from, main, "bus"));
        }
    }

    @Test void completeNodeViaConnectivityProjectsEachIncomingLaneOntoItsBranch() throws Exception {
        String relation = """
                <relation id="%d"><member type="way" ref="486434950" role="from"/>
                <member type="node" ref="352839257" role="via"/><member type="way" ref="%d" role="to"/>
                <tag k="type" v="connectivity"/><tag k="connectivity" v="%s"/></relation>
                """;
        String connections = relation.formatted(101, 5196943, "1:1") + relation.formatted(102, 75795034, "2:1|3:2");
        assertFork(genevaFork("through|through|through", connections), true, 5196943, 75795034, 75795034);
    }

    @Test void fiveRealApproachesRetainBothDrivingConnectivityAndSignedRestrictions() {
        Path file = Path.of("src/test/resources/osm/swiss-fork-approaches.osm");
        for (boolean withLanes : List.of(false, true)) {
            OsmData data = withLanes ? new OsmLaneData() : new OsmDataImpl();
            new OsmFileReader(data).readFile(file.toString());
            var config = OsmAdditionalTurnInformationTest.config(true);
            config.setKeepPaths(false);
            config.setOutputCoordinateSystem("EPSG:2056");
            for (String type : List.of("secondary", "trunk", "trunk_link", "motorway", "motorway_link")) {
                config.addParameterSet(new OsmConverterConfigGroup.OsmWayParams("highway", type, 1, 20, 1,
                        1500, type.startsWith("motorway"), Set.of("car", "bus")));
            }
            var converter = withLanes ? new OsmNetworkWithLanesConverter(data) : new OsmMultimodalNetworkConverter(data);
            converter.convert(config);

            checkMovement(converter, 1150175690, 248530969, 389763309, true);   // Neuchâtel: two through lanes, different destinations
            checkMovement(converter, 43087020, 60874506, 43087028, true);       // Brig: entry two links upstream
            checkMovement(converter, 43087028, 60874507, 718940895, true);
            checkMovement(converter, 718940895, 6750504349L, 718940896, true);
            checkMovement(converter, 1205306906, 12288372139L, 1328181794, true); // Wil: intermediate junction on the incoming OSM way
            checkMovement(converter, 1328181794, 121949770, 13220347, true);
            checkMovement(converter, 147113440, 1603210809, 147113438, true);  // St. Gallen: wide left fork
            checkMovement(converter, 147113438, 60767255, 24937946, true);
            checkMovement(converter, 295215099, 29758016, 365181489, true);    // Cornol: Transjurane mainline
            checkMovement(converter, 43087027, 60874507, 718940895, false);
            checkMovement(converter, 4853407, 60874507, 718940895, false);
            checkMovement(converter, 43087028, 60874506, 43087028, false);
            checkMovement(converter, 147113443, 1603210809, 147113438, false);
            checkMovement(converter, 24937943, 60767255, 24937946, false);
            if (withLanes) {
                var detailed = (OsmNetworkWithLanesConverter) converter;
                Link from = incoming(detailed, 1150175690, 248530969);
                var lanes = detailed.getLanes().getLanesToLinkAssignments().get(from.getId()).getLanes();
                assertEquals(List.of(link(detailed, 389763309, "248530969").getId()),
                        lanes.get(Id.create(from.getId() + ".1", Lane.class)).getToLinkIds());
                assertEquals(List.of(link(detailed, 1150175691, "248530969").getId()),
                        lanes.get(Id.create(from.getId() + ".2", Lane.class)).getToLinkIds());
            }
        }
    }

    private static Link incoming(OsmMultimodalNetworkConverter c, int way, long via) {
        return c.getNetwork().getNodes().get(Id.createNodeId(via)).getInLinks().values().stream()
                .filter(l -> c.osmIds.get(l.getId()).toString().equals(String.valueOf(way)))
                .filter(l -> l.getAllowedModes().contains("car")).findFirst().orElseThrow();
    }

    private static void checkMovement(OsmMultimodalNetworkConverter c, int from, long via, int to, boolean allowed) {
        Link approach = incoming(c, from, via);
        assertEquals(allowed, permitted(approach, link(c, to, String.valueOf(via)), "car"), from + " via " + via + " to " + to);
    }

    @Test void convertersAgreeAtRealWehntalerJunction() {
        assertParity(Path.of("src/test/resources/osm/wehntaler-junction.osm"));
    }
    @Test void busExemptionUsesSameSignedModeResolver() throws Exception {
        Path file = temp.resolve("bus-exemption.osm");
        Files.writeString(file, """
                <osm version="0.6">
                <node id="1" lon="0" lat="-0.001"/><node id="2" lon="0" lat="0"/>
                <node id="4" lon="0" lat="0.001"/>
                """ + OsmAdditionalTurnInformationTest.way(10, "1,2", tag("oneway", "no"))
                + OsmAdditionalTurnInformationTest.way(30, "2,4", "") + """
                <relation id="100"><member type="way" ref="10" role="from"/>
                <member type="node" ref="2" role="via"/><member type="way" ref="10" role="to"/>
                <tag k="type" v="restriction"/><tag k="restriction" v="no_u_turn"/><tag k="except" v="bus"/>
                </relation></osm>
                """);
        assertParity(file);
    }
}
