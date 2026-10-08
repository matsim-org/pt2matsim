package org.matsim.pt2matsim.lanes;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.matsim.api.core.v01.*;
import org.matsim.api.core.v01.network.*;
import org.matsim.core.network.NetworkUtils;
import org.matsim.lanes.*;
import org.matsim.pt2matsim.mapping.stopMatching.StopLinkPreparation;
import org.matsim.pt2matsim.osm.LaneConversionReport;

class StopMatchedLanesAdapterTest {
    private final Network network = NetworkUtils.createNetwork();
    private final Lanes lanes = LanesUtils.createLanesContainer();

    private Link link(String id, String from, String to, double length) {
        var f = network.getFactory();
        for (String node : List.of(from, to)) if (!network.getNodes().containsKey(Id.createNodeId(node)))
            network.addNode(f.createNode(Id.createNodeId(node), new Coord(network.getNodes().size() * 100, 0)));
        Link link = f.createLink(Id.createLinkId(id), network.getNodes().get(Id.createNodeId(from)), network.getNodes().get(Id.createNodeId(to)));
        link.setLength(length); link.setFreespeed(10); link.setCapacity(2000); link.setNumberOfLanes(2);
        link.setAllowedModes(Set.of("bus", "car")); network.addLink(link); return link;
    }

    private Lane assignment(String link, double fullLength, String destination) {
        var a = lanes.getFactory().createLanesToLinkAssignment(Id.createLinkId(link));
        Lane root = lanes.getFactory().createLane(Id.create(link + ".ol", Lane.class));
        root.setStartsAtMeterFromLinkEnd(fullLength); root.setCapacityVehiclesPerHour(2000); root.setNumberOfRepresentedLanes(2);
        Lane terminal = lanes.getFactory().createLane(Id.create(link + ".1", Lane.class));
        terminal.setStartsAtMeterFromLinkEnd(50); terminal.setCapacityVehiclesPerHour(1000); terminal.setNumberOfRepresentedLanes(1);
        terminal.addToLinkId(Id.createLinkId(destination)); root.addToLaneId(terminal.getId());
        a.addLane(root); a.addLane(terminal); lanes.addLanesToLinkAssignment(a); return terminal;
    }

    private void fragments(double lastLength) {
        Link first = link("stopMatch:road:1", "a", "private", 100 - lastLength);
        Link last = link("road", "private", "b", lastLength);
        first.getAttributes().putAttribute(StopLinkPreparation.ORIGINAL_LINK, "road");
        first.getAttributes().putAttribute(StopLinkPreparation.PART_INDEX, 0);
        last.getAttributes().putAttribute(StopLinkPreparation.ORIGINAL_LINK, "road");
        last.getAttributes().putAttribute(StopLinkPreparation.PART_INDEX, 1);
    }

    @Test void remapsIncomingTurnsAndFitsJunctionLanesWithoutIncreasingCapacity() {
        link("incoming", "start", "a", 100); fragments(40); link("exit", "b", "end", 100);
        Lane incoming = assignment("incoming", 100, "road");
        Lane terminal = assignment("road", 100, "exit");
        var report = new LaneConversionReport(); StopMatchedLanesAdapter.adapt(network, lanes, report);
        assertEquals(List.of(Id.createLinkId("stopMatch:road:1")), incoming.getToLinkIds());
        assertEquals(List.of(Id.createLinkId("exit")), terminal.getToLinkIds());
        assertEquals(20, terminal.getStartsAtMeterFromLinkEnd());
        assertEquals(1000, terminal.getCapacityVehiclesPerHour()); assertEquals(1, terminal.getNumberOfRepresentedLanes());
        var assignment = lanes.getLanesToLinkAssignments().get(Id.createLinkId("road"));
        assertEquals(40, assignment.getLanes().get(Id.create("road.ol", Lane.class)).getStartsAtMeterFromLinkEnd());
        assertEquals(1L, report.getCounts().get("stopMatchingLaneLengthsAdjusted"));
        assertFalse(lanes.getLanesToLinkAssignments().containsKey(Id.createLinkId("stopMatch:road:1")));
        LanesUtils.createLanes(network.getLinks().get(Id.createLinkId("road")), assignment);
    }

    @Test void preservesExistingLaneSectionLengthsWhenTheyFit() {
        fragments(80); link("exit", "b", "end", 100);
        Lane terminal = assignment("road", 100, "exit");
        var report = new LaneConversionReport(); StopMatchedLanesAdapter.adapt(network, lanes, report);
        assertEquals(50, terminal.getStartsAtMeterFromLinkEnd());
        assertFalse(report.getCounts().containsKey("stopMatchingLaneLengthsAdjusted"));
        assertEquals(1L, report.getCounts().get("stopMatchingLaneAssignmentsAdapted"));
    }
}
