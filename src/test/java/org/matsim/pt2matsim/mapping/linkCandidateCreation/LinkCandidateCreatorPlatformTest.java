package org.matsim.pt2matsim.mapping.linkCandidateCreation;

import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.network.NetworkUtils;
import org.matsim.pt.transitSchedule.api.Departure;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitScheduleFactory;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;
import org.matsim.pt2matsim.config.OsmConverterConfigGroup;
import org.matsim.pt2matsim.config.PublicTransitMappingConfigGroup;
import org.matsim.pt2matsim.config.PublicTransitMappingStrings;
import org.matsim.pt2matsim.config.TransportModeParameterSet;
import org.matsim.pt2matsim.mapping.PTMapper;
import org.matsim.pt2matsim.tools.ScheduleTools;

/**
 * Two parallel tracks: track 1 at y=0, track 2 at y=20. The stops are closer to track 1, but their platform code is 2.
 */
class LinkCandidateCreatorPlatformTest {

	private static final LinkCandidateCreator EMPTY = (routeStop, line, route) -> new TreeSet<>();

	private Network network;
	private TransitSchedule schedule;
	private TransitLine line;
	private TransitRoute route;

	@BeforeEach
	void setUp() {
		network = NetworkUtils.createNetwork();
		createTrack("1", 0);
		createTrack("2", 20);

		schedule = ScheduleTools.createSchedule();
		TransitScheduleFactory f = schedule.getFactory();
		TransitStopFacility s1 = createStop(f, "S1", new Coord(500, 2), "2");
		TransitStopFacility s2 = createStop(f, "S2", new Coord(1000, 2), "2");
		route = f.createTransitRoute(Id.create("r", TransitRoute.class), null,
				List.of(f.createTransitRouteStop(s1, 0, 0), f.createTransitRouteStop(s2, 60, 60)), "rail");
		route.addDeparture(f.createDeparture(Id.create("d", Departure.class), 8 * 3600));
		line = f.createTransitLine(Id.create("line", TransitLine.class));
		line.addRoute(route);
		schedule.addTransitLine(line);
	}

	private void createTrack(String track, double y) {
		Node prev = null;
		for (int i = 0; i < 4; i++) {
			Node node = NetworkUtils.createAndAddNode(network, Id.createNodeId(track + "_" + i), new Coord(i * 500, y));
			if (i == 1 || i == 2) {
				node.getAttributes().putAttribute(OsmConverterConfigGroup.NODE_ATTRIBUTE_RAIL_STOP_POSITION, true);
				node.getAttributes().putAttribute(OsmConverterConfigGroup.NODE_ATTRIBUTE_LOCAL_REF, track);
			}
			if (prev != null) {
				Link l = NetworkUtils.createAndAddLink(network, Id.createLinkId(track + "_" + (i - 1) + "_" + i), prev, node, 500, 30, 1000, 1);
				l.setAllowedModes(Set.of("rail"));
			}
			prev = node;
		}
	}

	private TransitStopFacility createStop(TransitScheduleFactory f, String id, Coord coord, String platform) {
		TransitStopFacility stop = f.createTransitStopFacility(Id.create(id, TransitStopFacility.class), coord, false);
		stop.getAttributes().putAttribute(PublicTransitMappingStrings.STOP_FACILITY_ATTRIBUTE_PLATFORM_CODE, platform);
		schedule.addStopFacility(stop);
		return stop;
	}

	private static PublicTransitMappingConfigGroup config(Set<String> platformModes) {
		PublicTransitMappingConfigGroup config = new PublicTransitMappingConfigGroup();
		config.setNumOfThreads(1);
		config.setPlatformModes(platformModes);
		TransportModeParameterSet rail = new TransportModeParameterSet("rail");
		rail.setNetworkModesStr("rail");
		config.addParameterSet(rail);
		return config;
	}

	@Test
	void candidatesAreInLinksOfMatchingStopPosition() {
		LinkCandidateCreatorPlatform creator = new LinkCandidateCreatorPlatform(EMPTY, schedule, network, config(Set.of("rail")));
		SortedSet<LinkCandidate> c1 = creator.getLinkCandidates(route.getStops().get(0), line, route);
		SortedSet<LinkCandidate> c2 = creator.getLinkCandidates(route.getStops().get(1), line, route);
		Assertions.assertEquals(1, c1.size());
		Assertions.assertEquals(Id.createLinkId("2_0_1"), c1.first().getLink().getId());
		Assertions.assertEquals(Id.createLinkId("2_1_2"), c2.first().getLink().getId());
	}

	@Test
	void otherModesUseDelegate() {
		LinkCandidateCreatorPlatform creator = new LinkCandidateCreatorPlatform(EMPTY, schedule, network, config(Set.of("tram")));
		Assertions.assertTrue(creator.getLinkCandidates(route.getStops().get(0), line, route).isEmpty());
	}

	@Test
	void stopsWithoutPlatformCodeOrMatchUseDelegate() {
		route.getStops().get(0).getStopFacility().getAttributes().removeAttribute(PublicTransitMappingStrings.STOP_FACILITY_ATTRIBUTE_PLATFORM_CODE);
		route.getStops().get(1).getStopFacility().getAttributes().putAttribute(PublicTransitMappingStrings.STOP_FACILITY_ATTRIBUTE_PLATFORM_CODE, "7");
		LinkCandidateCreatorPlatform creator = new LinkCandidateCreatorPlatform(EMPTY, schedule, network, config(Set.of("rail")));
		Assertions.assertTrue(creator.getLinkCandidates(route.getStops().get(0), line, route).isEmpty());
		Assertions.assertTrue(creator.getLinkCandidates(route.getStops().get(1), line, route).isEmpty());
	}

	@Test
	void platformMatchesMultipleLocalRefs() {
		Assertions.assertTrue(LinkCandidateCreatorPlatform.platformMatches("3", "3;4"));
		Assertions.assertTrue(LinkCandidateCreatorPlatform.platformMatches("4a", "4A"));
		Assertions.assertFalse(LinkCandidateCreatorPlatform.platformMatches("1", "11"));
		Assertions.assertFalse(LinkCandidateCreatorPlatform.platformMatches("1", null));
	}

	@Test
	void ptMapperUsesClosestTrackByDefault() throws Exception {
		new PTMapper(schedule, network).run(config(Set.of()));
		List<Id<Link>> links = mappedStopLinks();
		Assertions.assertTrue(links.stream().allMatch(id -> id.toString().startsWith("1_")), links.toString());
	}

	@Test
	void ptMapperUsesPlatformTrack() throws Exception {
		new PTMapper(schedule, network).run(config(Set.of("rail")));
		Assertions.assertEquals(List.of(Id.createLinkId("2_0_1"), Id.createLinkId("2_1_2")), mappedStopLinks());
	}

	@Test
	void ptMapperUsesOutLinksOfStopPositions() throws Exception {
		PublicTransitMappingConfigGroup config = config(Set.of("rail"));
		config.setPlatformStopLink(PublicTransitMappingConfigGroup.PlatformStopLink.outLink);
		new PTMapper(schedule, network).run(config);
		Assertions.assertEquals(List.of(Id.createLinkId("2_1_2"), Id.createLinkId("2_2_3")), mappedStopLinks());
	}

	private List<Id<Link>> mappedStopLinks() {
		TransitRoute mapped = schedule.getTransitLines().get(line.getId()).getRoutes().get(route.getId());
		return mapped.getStops().stream().map(s -> s.getStopFacility().getLinkId()).toList();
	}
}
