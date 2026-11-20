package org.matsim.pt2matsim.tools;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.population.routes.RouteUtils;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitRouteStop;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitScheduleFactory;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

class PullChildStopFacilitiesTest {

	private Network network;
	private TransitSchedule schedule;
	private TransitRoute route;
	private TransitStopFacility firstStop;

	/**
	 * n1 --l12--> n2 --l23--> n3, first stop is close to l12 but references l23.
	 */
	@BeforeEach
	void setUp() {
		network = NetworkUtils.createNetwork();
		Node n1 = NetworkUtils.createAndAddNode(network, Id.createNodeId("n1"), new Coord(0, 0));
		Node n2 = NetworkUtils.createAndAddNode(network, Id.createNodeId("n2"), new Coord(100, 0));
		Node n3 = NetworkUtils.createAndAddNode(network, Id.createNodeId("n3"), new Coord(200, 0));
		Link l12 = NetworkUtils.createAndAddLink(network, Id.createLinkId("l12"), n1, n2, 100, 10, 1000, 1);
		Link l23 = NetworkUtils.createAndAddLink(network, Id.createLinkId("l23"), n2, n3, 100, 10, 1000, 1);
		l12.setAllowedModes(Set.of("rail"));
		l23.setAllowedModes(Set.of("rail"));

		schedule = ScheduleTools.createSchedule();
		TransitScheduleFactory f = schedule.getFactory();
		firstStop = f.createTransitStopFacility(Id.create("A", TransitStopFacility.class), new Coord(10, 5), false);
		firstStop.setLinkId(l23.getId());
		TransitStopFacility lastStop = f.createTransitStopFacility(Id.create("B", TransitStopFacility.class), new Coord(190, 5), false);
		lastStop.setLinkId(l23.getId());
		schedule.addStopFacility(firstStop);
		schedule.addStopFacility(lastStop);

		List<TransitRouteStop> stops = List.of(
				f.createTransitRouteStop(firstStop, 0, 0),
				f.createTransitRouteStop(lastStop, 60, 60));
		route = f.createTransitRoute(Id.create("r", TransitRoute.class),
				RouteUtils.createNetworkRoute(List.of(l23.getId())), stops, "rail");
		TransitLine line = f.createTransitLine(Id.create("line", TransitLine.class));
		line.addRoute(route);
		schedule.addTransitLine(line);
	}

	@Test
	void pullsByDefault() {
		Assertions.assertEquals(1, PTMapperTools.pullChildStopFacilitiesTogether(schedule, network));
		Assertions.assertEquals(Id.createLinkId("l12"), route.getStops().get(0).getStopFacility().getLinkId());
		Assertions.assertEquals(Id.createLinkId("l12"), ScheduleTools.getTransitRouteLinkIds(route).get(0));
	}

	@Test
	void excludedModesAreNotPulled() {
		List<Id<Link>> before = ScheduleTools.getTransitRouteLinkIds(route);
		Assertions.assertEquals(0, PTMapperTools.pullChildStopFacilitiesTogether(schedule, network, Set.of("rail")));
		Assertions.assertSame(firstStop, route.getStops().get(0).getStopFacility());
		Assertions.assertEquals(before, ScheduleTools.getTransitRouteLinkIds(route));
	}

	@Test
	void otherModesAreStillPulled() {
		Assertions.assertEquals(1, PTMapperTools.pullChildStopFacilitiesTogether(schedule, network, Set.of("bus")));
	}
}
