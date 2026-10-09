package org.matsim.pt2matsim.osm;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.matsim.api.core.v01.network.Link;
import org.matsim.pt2matsim.config.OsmConverterConfigGroup;
import org.matsim.pt2matsim.osm.lib.Osm;
import org.matsim.pt2matsim.osm.lib.OsmDataImpl;
import org.matsim.pt2matsim.osm.lib.OsmFileReader;
import static org.junit.jupiter.api.Assertions.*;

class OsmReservedLaneArrayValidationTest {
    @TempDir Path temporary;

    @Test void inconsistentBackwardArrayKeepsCarLaneAndOnlyOneForwardReservedLink() throws Exception {
        var c = convert(Map.of("psv:lanes:backward", "yes|designated"));
        var links = c.getNetwork().getLinks().values();
        assertEquals(3, links.size());
        assertTrue(links.stream().allMatch(l -> l.getNumberOfLanes() == 1 && l.getCapacity() == 1500));
        Link reserved = links.stream().filter(l -> l.getId().toString().endsWith("_spec")).findFirst().orElseThrow();
        assertEquals("1", reserved.getFromNode().getId().toString());
        assertEquals("2", reserved.getToNode().getId().toString());
        assertTrue(links.stream().anyMatch(l -> l.getFromNode().getId().toString().equals("2")
                && l.getAllowedModes().contains("car")));
        assertEquals(List.of("psv:lanes:backward"), c.ignored);
    }

    @Test void consistentForwardArrayPreservesReservedLane() throws Exception {
        var c = convert(Map.of("psv:lanes:forward", "yes|designated"));
        assertEquals(3, c.getNetwork().getLinks().size());
        assertTrue(c.ignored.isEmpty());
    }

    @Test void explicitlyNonMotorSlotDoesNotInvalidateReservedArray() throws Exception {
        // Remove the numerical reserved count so reservation is derived from the array.
        var c = convert(Map.of("psv:lanes:forward", "yes|no|designated",
                "motor_vehicle:lanes:forward", "yes|no|yes", "bicycle:lanes:forward", "no|designated|no"));
        assertEquals(3, c.getNetwork().getLinks().size());
        assertTrue(c.getNetwork().getLinks().values().stream().allMatch(l -> l.getNumberOfLanes() == 1));
        assertTrue(c.ignored.isEmpty());
    }

    private AuditedConverter convert(Map<String,String> extra) throws Exception {
        var tags = new java.util.LinkedHashMap<>(Map.of("highway", "primary", "oneway", "no", "lanes", "3",
                "lanes:forward", "2", "lanes:backward", "1", "lanes:psv:forward", "1"));
        if (extra.containsKey("psv:lanes:forward")) tags.remove("lanes:psv:forward");
        tags.putAll(extra);
        StringBuilder xml = new StringBuilder("<osm version=\"0.6\"><node id=\"1\" lon=\"6.1538149\" lat=\"46.1998970\"/>"
                + "<node id=\"2\" lon=\"6.1536235\" lat=\"46.2000382\"/><way id=\"196394133\"><nd ref=\"1\"/><nd ref=\"2\"/>");
        tags.forEach((key,value) -> xml.append("<tag k=\"").append(key).append("\" v=\"").append(value).append("\"/>"));
        xml.append("</way></osm>");
        Path input = temporary.resolve("input.osm"); Files.writeString(input,xml);
        var data = new OsmDataImpl(); new OsmFileReader(data).readFile(input.toString());
        var config = new OsmConverterConfigGroup(); config.setKeepPaths(true); config.setOutputCoordinateSystem("EPSG:2056");
        config.addParameterSet(new OsmConverterConfigGroup.OsmWayParams("highway", "primary", 1, 10, 1, 1500, false, Set.of("car","bus","taxi")));
        var converter = new AuditedConverter(data); converter.convert(config); return converter;
    }

    private static final class AuditedConverter extends OsmMultimodalNetworkConverter {
        final List<String> ignored = new ArrayList<>();
        AuditedConverter(OsmDataImpl data) { super(data); }
        @Override protected void onInconsistentReservedLaneArray(Osm.Way way,String key,String value,int slots,double declared) {
            ignored.add(key);
        }
    }
}
