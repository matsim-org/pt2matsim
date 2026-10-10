package org.matsim.pt2matsim.osm;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.matsim.api.core.v01.network.Link;
import org.matsim.pt2matsim.config.OsmConverterConfigGroup;
import org.matsim.pt2matsim.osm.lib.*;
import org.matsim.core.network.NetworkUtils;
import static org.junit.jupiter.api.Assertions.*;

class OsmAdditionalTurnInformationTest {
    @TempDir Path temp;
    private static final String NODES = """
        <node id="1" lon="0" lat="-0.001"/><node id="2" lon="0" lat="0"/>
        <node id="3" lon="-0.001" lat="0"/><node id="4" lon="0" lat="0.001"/>
        <node id="5" lon="0.001" lat="0"/>
        """;
    static String tag(String key, String value) { return "<tag k=\"" + key + "\" v=\"" + value + "\"/>"; }
    static String way(int id, String nodes, String tags) {
        StringBuilder result = new StringBuilder("<way id=\"" + id + "\">");
        for (String node : nodes.split(",")) result.append("<nd ref=\"").append(node).append("\"/>");
        return result + tag("highway", "primary") + tag("oneway", "yes") + tags + "</way>";
    }
    static OsmConverterConfigGroup config(boolean enabled) {
        var config = new OsmConverterConfigGroup();
        config.addParameterSet(new OsmConverterConfigGroup.OsmWayParams("highway", "primary", 1, 10, 1, 1500, true, Set.of("car", "bus", "bike")));
        config.setOutputCoordinateSystem("EPSG:3857");
        config.setKeepPaths(true);
        config.setUseOsmTurnArrows(enabled);
        return config;
    }
    OsmMultimodalNetworkConverter convert(String nodes, String incoming, String outgoing, String relation, boolean enabled) throws Exception {
        Path file = temp.resolve("input.osm");
        Files.writeString(file, "<osm version=\"0.6\">" + nodes + way(10, "1,2", incoming) + outgoing + relation + "</osm>");
        var data = new OsmDataImpl();
        new OsmFileReader(data).readFile(file.toString());
        var converter = new OsmMultimodalNetworkConverter(data);
        var config = config(enabled);
        config.setOutputTurnRestrictionsReportFile(temp.resolve("turn-report.csv").toString());
        converter.convert(config);
        return converter;
    }
    OsmMultimodalNetworkConverter junction(String incoming, boolean enabled) throws Exception {
        return convert(NODES, incoming, way(20, "2,3", "") + way(30, "2,4", "") + way(40, "2,5", ""), "", enabled);
    }
    static Link link(OsmMultimodalNetworkConverter c, int way, String from) {
        return c.getNetwork().getLinks().values().stream()
                .filter(l -> c.osmIds.get(l.getId()).toString().equals("" + way) && l.getFromNode().getId().toString().equals(from))
                .filter(l -> !l.getId().toString().endsWith("_spec")).findFirst().orElseThrow();
    }
    static boolean permitted(Link from, Link out, String mode) {
        var dnl = NetworkUtils.getDisallowedNextLinks(from);
        return from.getAllowedModes().contains(mode) && out.getAllowedModes().contains(mode)
                && (dnl == null || !dnl.getDisallowedLinkSequences(mode).contains(List.of(out.getId())));
    }
    @Test void disabledByDefaultAndDoesNotInterpretArrows() throws Exception {
        assertFalse(new OsmConverterConfigGroup().getUseOsmTurnArrows());
        var c = junction(tag("turn:lanes", "left"), false);
        for (int way : List.of(20, 30, 40)) assertTrue(permitted(link(c, 10, "1"), link(c, way, "2"), "car"));
        assertTrue(c.getAdditionalTurnRestrictionCounts().isEmpty());
        assertFalse(Files.exists(temp.resolve("turn-report.csv")));
    }
    @Test void arrowsConstrainMovementsAndAuditUnservedExits() throws Exception {
        var c = junction(tag("turn:lanes", "left"), true);
        Link from = link(c, 10, "1");
        assertTrue(permitted(from, link(c, 20, "2"), "car"));
        assertFalse(permitted(from, link(c, 30, "2"), "car"));
        assertFalse(permitted(from, link(c, 40, "2"), "bus"));
        assertTrue(Files.readString(temp.resolve("turn-report.csv")).contains("unservedLegalMovements"));
    }
    @Test void missingArrowsBanMotorUTurnsButRetainNonMotorAndOtherExits() throws Exception {
        var c = junction(tag("oneway", "no"), true);
        Link from = link(c, 10, "1"), reverse = link(c, 10, "2");
        assertFalse(permitted(from, reverse, "car"));
        assertFalse(permitted(from, reverse, "bus"));
        assertTrue(permitted(from, reverse, "bike"));
        for (int way : List.of(20, 30, 40)) assertTrue(permitted(from, link(c, way, "2"), "car"));
        assertTrue(c.getAdditionalTurnRestrictionCounts().get("inferredUTurnModeBans") > 0);
    }
    @Test void explicitReverseArrowAndUnmatchedArrowFollowExistingFallback() throws Exception {
        var c = junction(tag("oneway", "no") + tag("turn:lanes:forward", "reverse"), true);
        assertTrue(permitted(link(c, 10, "1"), link(c, 10, "2"), "car"));
        assertFalse(permitted(link(c, 10, "1"), link(c, 30, "2"), "car"));
        c = junction(tag("turn:lanes", "unrecognised"), true);
        for (int way : List.of(20, 30, 40)) assertTrue(permitted(link(c, 10, "1"), link(c, way, "2"), "car"));
        assertTrue(c.getAdditionalTurnRestrictionCounts().get("inferredLaneMovements") > 0);
    }
    @Test void retainsOnlyExitAndTurningFacility() throws Exception {
        var c = convert(NODES, tag("oneway", "no"), "", "", true);
        assertTrue(permitted(link(c, 10, "1"), link(c, 10, "2"), "car"));
        assertEquals(0L, c.getAdditionalTurnRestrictionCounts().get("inferredUTurnModeBans"));
        String nodes = NODES.replace("<node id=\"2\" lon=\"0\" lat=\"0\"/>",
                "<node id=\"2\" lon=\"0\" lat=\"0\">" + tag("highway", "turning_circle") + "</node>");
        c = convert(nodes, tag("oneway", "no"), way(30, "2,4", ""), "", true);
        assertTrue(permitted(link(c, 10, "1"), link(c, 10, "2"), "car"));
    }
    @Test void cyclingSlotDoesNotDonateItsArrowToMotorLanes() throws Exception {
        var c = junction(tag("lanes", "2") + tag("turn:lanes", "left|through|right")
                + tag("motor_vehicle:lanes", "yes|no|yes") + tag("bicycle:lanes", "yes|designated|yes"), true);
        assertTrue(permitted(link(c, 10, "1"), link(c, 20, "2"), "car"));
        assertFalse(permitted(link(c, 10, "1"), link(c, 30, "2"), "car"));
        assertTrue(permitted(link(c, 10, "1"), link(c, 40, "2"), "car"));
        assertTrue(c.getAdditionalTurnRestrictionCounts().get("nonMotorLaneSlotsExcluded") > 0);
    }
    @Test void signedBanIsNeverReopenedByArrow() throws Exception {
        String restriction = "<relation id=\"100\"><member type=\"way\" ref=\"10\" role=\"from\"/>"
                + "<member type=\"node\" ref=\"2\" role=\"via\"/><member type=\"way\" ref=\"20\" role=\"to\"/>"
                + tag("type", "restriction") + tag("restriction", "no_left_turn") + "</relation>";
        var c = convert(NODES, tag("turn:lanes", "left"), way(20, "2,3", "") + way(30, "2,4", ""), restriction, true);
        assertFalse(permitted(link(c, 10, "1"), link(c, 20, "2"), "car"));
        assertTrue(permitted(link(c, 10, "1"), link(c, 30, "2"), "car"));
    }
    @Test void signedBusUTurnExemptionSurvivesMissingArrowInference() throws Exception {
        String rule = "<relation id=\"100\"><member type=\"way\" ref=\"10\" role=\"from\"/>"
                + "<member type=\"node\" ref=\"2\" role=\"via\"/><member type=\"way\" ref=\"10\" role=\"to\"/>"
                + tag("type", "restriction") + tag("restriction", "no_u_turn") + tag("except", "bus") + "</relation>";
        var c = convert(NODES, tag("oneway", "no"), way(30, "2,4", ""), rule, true);
        assertFalse(permitted(link(c, 10, "1"), link(c, 10, "2"), "car"));
        assertTrue(permitted(link(c, 10, "1"), link(c, 10, "2"), "bus"));
        assertTrue(c.getAdditionalTurnRestrictionCounts().get("uTurnModeExemptionExceptions") > 0);
    }
    @Test void configOptionsRoundTrip() {
        var original = config(true);
        original.setOutputTurnRestrictionsReportFile("turn-decisions.csv");
        Path xml = temp.resolve("config.xml");
        org.matsim.core.config.ConfigUtils.writeConfig(org.matsim.core.config.ConfigUtils.createConfig(original), xml.toString());
        var restored = new OsmConverterConfigGroup();
        org.matsim.core.config.ConfigUtils.loadConfig(xml.toString(), restored);
        assertTrue(restored.getUseOsmTurnArrows());
        assertEquals("turn-decisions.csv", restored.getOutputTurnRestrictionsReportFile());
    }
    @Test void enabledRequiresTurnRestrictionParsing() {
        var config = config(true);
        config.parseTurnRestrictions = false;
        assertThrows(IllegalArgumentException.class, () -> new OsmMultimodalNetworkConverter(new OsmDataImpl()).convert(config));
    }
}
