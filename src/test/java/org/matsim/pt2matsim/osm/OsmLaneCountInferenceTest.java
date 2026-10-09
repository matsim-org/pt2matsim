package org.matsim.pt2matsim.osm;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.matsim.api.core.v01.network.Link;
import org.matsim.pt2matsim.config.OsmConverterConfigGroup;
import org.matsim.pt2matsim.osm.lib.*;
import static org.junit.jupiter.api.Assertions.*;

class OsmLaneCountInferenceTest {
    @TempDir Path temporary;

    @Test void rueDuStandKeepsSharedCarLaneAndOneReservedLane() throws Exception {
        var c=convert(Map.of("psv:lanes","yes|designated"));
        assertSplit(c,"1");assertEquals(List.of(2),c.inferences);
    }
    @Test void reverseOnewayUsesArraysInTravelDirection() throws Exception {
        var c=convert(Map.of("oneway","-1","psv:lanes","yes|designated"));assertSplit(c,"2");assertEquals(List.of(2),c.inferences);
    }
    @Test void directedArrayInfersOnlyItsDirectionOnTwoWayRoad() throws Exception {
        var c=convert(Map.of("oneway","no","psv:lanes:forward","yes|designated"));
        assertEquals(3,c.getNetwork().getLinks().size());
        assertEquals(1,c.getNetwork().getLinks().values().stream().filter(l->l.getId().toString().endsWith("_spec")).count());
        assertTrue(c.getNetwork().getLinks().values().stream().allMatch(l->l.getNumberOfLanes()==1));
        assertEquals(List.of(2),c.inferences);
    }
    @Test void explicitCountsHavePriorityOverLaneArrays() throws Exception {
        var c=convert(Map.of("lanes","3","psv:lanes","yes|designated"));
        assertEquals(2,c.getNetwork().getLinks().values().stream().filter(l->!l.getId().toString().endsWith("_spec")).findFirst().orElseThrow().getNumberOfLanes());
        assertTrue(c.inferences.isEmpty());
    }
    @Test void conflictingArraysAreReportedAndKeepDefault() throws Exception {
        var c=convert(Map.of("psv:lanes","yes|yes","turn:lanes","left|through|right"));
        assertEquals(1,c.getNetwork().getLinks().values().iterator().next().getNumberOfLanes());assertEquals(List.of(0),c.inferences);
    }
    @Test void explicitCyclingSlotIsNotCountedAsMotorLane() throws Exception {
        var c=convert(Map.of("psv:lanes","yes|no|designated","motor_vehicle:lanes","yes|no|yes","bicycle:lanes","no|designated|no"));
        assertSplit(c,"1");assertEquals(List.of(2),c.inferences);
    }
    @Test void emptyTrailingSlotIsPreserved() throws Exception { assertSplit(convert(Map.of("psv:lanes","designated|")),"1"); }
    @Test void undirectedTwoWayAndNumericReservationTagsDoNotSupplyTotal() throws Exception {
        assertNull(OsmLaneCountInference.infer(Map.of("psv:lanes","yes|designated"),true,false));
        assertNull(OsmLaneCountInference.infer(Map.of("bus:lanes","2"),true,true));
    }
    private void assertSplit(AuditedConverter c,String from) {
        var links=c.getNetwork().getLinks().values();assertEquals(2,links.size());
        assertTrue(links.stream().allMatch(l->l.getNumberOfLanes()==1 && l.getCapacity()==1500 && l.getFromNode().getId().toString().equals(from)));
        Link shared=links.stream().filter(l->!l.getId().toString().endsWith("_spec")).findFirst().orElseThrow();
        assertTrue(shared.getAllowedModes().contains("car"));
        assertTrue(links.stream().anyMatch(l->l.getId().toString().endsWith("_spec") && l.getAllowedModes().contains("bus")));
    }
    private AuditedConverter convert(Map<String,String> extra) throws Exception {
        Map<String,String> tags=new LinkedHashMap<>(Map.of("highway","residential","oneway","yes"));tags.putAll(extra);
        StringBuilder text=new StringBuilder("<osm version=\"0.6\"><node id=\"1\" lon=\"6.1392498\" lat=\"46.2034763\"/><node id=\"2\" lon=\"6.1398953\" lat=\"46.2035370\"/><way id=\"641609866\"><nd ref=\"1\"/><nd ref=\"2\"/>");
        tags.forEach((k,v)->text.append("<tag k=\"").append(k).append("\" v=\"").append(v).append("\"/>"));text.append("</way></osm>");
        Path input=temporary.resolve("input.osm");Files.writeString(input,text);var data=new OsmDataImpl();new OsmFileReader(data).readFile(input.toString());
        var config=new OsmConverterConfigGroup();config.setKeepPaths(true);config.setOutputCoordinateSystem("EPSG:2056");
        config.addParameterSet(new OsmConverterConfigGroup.OsmWayParams("highway","residential",1,10,1,1500,false,Set.of("car","bus","taxi")));
        var c=new AuditedConverter(data);c.convert(config);return c;
    }
    private static class AuditedConverter extends OsmMultimodalNetworkConverter {
        final List<Integer> inferences=new ArrayList<>();
        AuditedConverter(OsmDataImpl data){super(data);}
        @Override protected void onLaneCountFromTags(Osm.Way way,boolean forward,int count,String evidence){inferences.add(count);}
    }
}
