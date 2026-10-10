package org.matsim.pt2matsim.osm;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.matsim.api.core.v01.network.Link;
import org.matsim.core.network.NetworkUtils;
import org.matsim.pt2matsim.config.OsmConverterConfigGroup;
import org.matsim.pt2matsim.osm.lib.*;
import static org.junit.jupiter.api.Assertions.*;

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
