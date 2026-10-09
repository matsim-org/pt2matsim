package org.matsim.pt2matsim.osm;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.pt2matsim.config.OsmConverterConfigGroup;
import org.matsim.pt2matsim.config.OsmConverterConfigGroup.WayTagOverrideParams;
import org.matsim.pt2matsim.osm.lib.*;
import static org.junit.jupiter.api.Assertions.*;

class OsmWayTagOverrideTest {
    @TempDir Path temporary;

    @Test void restoresGeneralCapacityWithoutChangingReservedLinkOrOtherWays() throws Exception {
        OsmDataImpl data = data();
        var config = config();
        config.addParameterSet(correction("1354965562", "lanes", "2", "1"));
        var converter = new OsmMultimodalNetworkConverter(data);
        converter.convert(config);
        var links = converter.getNetwork().getLinks().values();
        Link general = links.stream().filter(l -> way(l) == 1354965562L && !l.getId().toString().endsWith("_spec")).findFirst().orElseThrow();
        Link reserved = links.stream().filter(l -> way(l) == 1354965562L && l.getId().toString().endsWith("_spec")).findFirst().orElseThrow();
        assertEquals(1, general.getNumberOfLanes());
        assertEquals(600, general.getCapacity());
        assertTrue(general.getAllowedModes().contains("car"));
        assertEquals(1, reserved.getNumberOfLanes());
        assertEquals(600, reserved.getCapacity());
        assertTrue(reserved.getAllowedModes().contains("bus"));
        assertFalse(reserved.getAllowedModes().contains("car"));
        assertEquals(1, links.stream().filter(l -> way(l) == 48564363L).findFirst().orElseThrow().getNumberOfLanes());
        assertTrue(Files.readString(temporary.resolve("input.osm")).contains("k=\"lanes\" v=\"1\""));
    }

    @Test void staleCorrectionFailsBeforeAnyTagIsChanged() throws Exception {
        var data = data();
        var config = config();
        config.addParameterSet(correction("48564363", "lanes", "3", "1"));
        config.addParameterSet(correction("1354965562", "lanes", "2", "7"));
        assertThrows(IllegalArgumentException.class, () -> new OsmMultimodalNetworkConverter(data).convert(config));
        assertEquals("1", data.getWays().get(Id.create("48564363", Osm.Way.class)).getTags().get("lanes"));
    }

    @Test void canRemoveIncorrectReservationTagForSharedLane() throws Exception {
        var data = data();
        var config = config();
        var correction = correction("1354965562", "lanes:psv", null, "1");
        correction.remove = true;
        config.addParameterSet(correction);
        var converter = new OsmMultimodalNetworkConverter(data);
        converter.convert(config);
        var links = converter.getNetwork().getLinks().values().stream().filter(l -> way(l) == 1354965562L).toList();
        assertEquals(1, links.size());
        assertEquals(1, links.getFirst().getNumberOfLanes());
        assertTrue(links.getFirst().getAllowedModes().containsAll(Set.of("car", "bus")));
    }

    @Test void duplicateCorrectionsAreRejected() throws Exception {
        var data = data();
        var config = config();
        config.addParameterSet(correction("1354965562", "lanes", "2", "1"));
        config.addParameterSet(correction("1354965562", "lanes", "3", "1"));
        assertThrows(IllegalArgumentException.class, () -> new OsmMultimodalNetworkConverter(data).convert(config));
    }

    @Test void configRoundTripAndMissingWayLeaveOtherWaysUnchanged() throws Exception {
        var config = config();
        var correction = correction("999999999", "lanes", "2", "1");
        correction.reason = "Known local data correction";
        config.addParameterSet(correction);
        Path file = temporary.resolve("config.xml");
        config.writeToFile(file.toString());
        var loaded = OsmConverterConfigGroup.loadConfig(file.toString());
        var reloaded = (WayTagOverrideParams) loaded.getParameterSets(WayTagOverrideParams.SET_NAME).iterator().next();
        assertEquals(correction.reason, reloaded.reason);
        assertEquals("1", reloaded.expectedValue);
        var converter = new OsmMultimodalNetworkConverter(data());
        converter.convert(loaded);
        assertEquals(0, converter.getNetwork().getLinks().values().stream().filter(l -> way(l) == 1354965562L && !l.getId().toString().endsWith("_spec")).findFirst().orElseThrow().getNumberOfLanes());
    }

    private static long way(Link link) {
        return (Long) link.getAttributes().getAttribute("osm:way:id");
    }

    private static WayTagOverrideParams correction(String id, String key, String value, String expected) {
        var correction = new WayTagOverrideParams();
        correction.wayId = id;
        correction.key = key;
        correction.value = value;
        correction.expectedValue = expected;
        return correction;
    }

    private OsmConverterConfigGroup config() {
        var config = new OsmConverterConfigGroup();
        config.setOutputCoordinateSystem("EPSG:2056");
        config.setKeepPaths(true);
        config.addParameterSet(new OsmConverterConfigGroup.OsmWayParams("highway", "unclassified", 1, 10, 1, 600, true, Set.of("car", "bus", "taxi")));
        return config;
    }

    private OsmDataImpl data() throws Exception {
        Path file = temporary.resolve("input.osm");
        Files.writeString(file, """
            <osm version="0.6">
              <node id="1" lon="6.146817" lat="46.2043303"/>
              <node id="2" lon="6.146644" lat="46.2043688"/>
              <node id="3" lon="6.1467876" lat="46.2046814"/>
              <way id="1354965562"><nd ref="1"/><nd ref="2"/>
                <tag k="highway" v="unclassified"/><tag k="oneway" v="yes"/>
                <tag k="lanes" v="1"/><tag k="lanes:psv" v="1"/>
              </way>
              <way id="48564363"><nd ref="2"/><nd ref="3"/>
                <tag k="highway" v="unclassified"/><tag k="oneway" v="yes"/><tag k="lanes" v="1"/>
              </way>
            </osm>
            """);
        var data = new OsmDataImpl();
        new OsmFileReader(data).readFile(file.toString());
        return data;
    }
}
