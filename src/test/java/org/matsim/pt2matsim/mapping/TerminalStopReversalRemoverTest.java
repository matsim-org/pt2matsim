package org.matsim.pt2matsim.mapping;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.population.routes.RouteUtils;
import org.matsim.pt.transitSchedule.api.Departure;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitRouteStop;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;
import org.matsim.pt2matsim.tools.ScheduleTools;

/**
 * Double track 0-1-2-3, links of 100 m in both directions.
 */
class TerminalStopReversalRemoverTest {

	@Test
	void removesReversalBeforeLastStop() {
		Network network = track();
		TransitSchedule schedule = ScheduleTools.createSchedule();
		TransitStopFacility first = facility(schedule, "first.link:0_1", "0_1");
		TransitStopFacility last = facility(schedule, "last.link:3_2", "3_2");
		TransitRoute route = route(schedule, "rail", links("0_1", "1_2", "2_3", "3_2"), first, last);

		Assertions.assertEquals(1, TerminalStopReversalRemover.run(schedule, network, Set.of("rail")));

		Assertions.assertEquals(links("0_1", "1_2", "2_3"), routeLinks(route));
		TransitStopFacility moved = route.getStops().get(1).getStopFacility();
		Assertions.assertEquals("last.link:2_3", moved.getId().toString());
		Assertions.assertEquals(Id.createLinkId("2_3"), moved.getLinkId());
		Assertions.assertEquals(Id.createLinkId("3_2"), last.getLinkId());
	}

	@Test
	void keepsRouteIfOppositeLinkIsBeforePreviousStop() {
		Network network = track();
		TransitSchedule schedule = ScheduleTools.createSchedule();
		TransitStopFacility first = facility(schedule, "first", "2_3");
		TransitStopFacility last = facility(schedule, "last", "3_2");
		TransitRoute route = route(schedule, "rail", links("0_1", "1_2", "2_3", "3_2"), first, last);

		Assertions.assertEquals(0, TerminalStopReversalRemover.run(schedule, network, Set.of("rail")));
		Assertions.assertEquals(links("0_1", "1_2", "2_3", "3_2"), routeLinks(route));
	}

	@Test
	void ignoresOtherModes() {
		Network network = track();
		TransitSchedule schedule = ScheduleTools.createSchedule();
		TransitStopFacility first = facility(schedule, "first", "0_1");
		TransitStopFacility last = facility(schedule, "last", "3_2");
		TransitRoute route = route(schedule, "bus", links("0_1", "1_2", "2_3", "3_2"), first, last);

		Assertions.assertEquals(0, TerminalStopReversalRemover.run(schedule, network, Set.of("rail")));
		Assertions.assertEquals(links("0_1", "1_2", "2_3", "3_2"), routeLinks(route));
	}

	private static Network track() {
		Network network = NetworkUtils.createNetwork();
		List<Node> nodes = new ArrayList<>();
		for (int i = 0; i < 4; i++) {
			nodes.add(NetworkUtils.createAndAddNode(network, Id.createNodeId(i), new Coord(100 * i, 0)));
		}
		for (int i = 0; i < 3; i++) {
			link(network, nodes.get(i), nodes.get(i + 1));
			link(network, nodes.get(i + 1), nodes.get(i));
		}
		return network;
	}

	private static void link(Network network, Node from, Node to) {
		Link l = NetworkUtils.createAndAddLink(network, Id.createLinkId(from.getId() + "_" + to.getId()), from, to, 100, 10, 1000, 1);
		l.setAllowedModes(Set.of("rail"));
	}

	private static TransitStopFacility facility(TransitSchedule schedule, String id, String linkId) {
		TransitStopFacility facility = schedule.getFactory().createTransitStopFacility(Id.create(id, TransitStopFacility.class), new Coord(0, 0), false);
		facility.setLinkId(Id.createLinkId(linkId));
		schedule.addStopFacility(facility);
		return facility;
	}

	private static TransitRoute route(TransitSchedule schedule, String mode, List<Id<Link>> links, TransitStopFacility... facilities) {
		List<TransitRouteStop> stops = new ArrayList<>();
		for (int i = 0; i < facilities.length; i++) {
			stops.add(schedule.getFactory().createTransitRouteStop(facilities[i], 60 * i, 60 * i));
		}
		TransitRoute route = schedule.getFactory().createTransitRoute(Id.create("r", TransitRoute.class), RouteUtils.createNetworkRoute(links), stops, mode);
		route.addDeparture(schedule.getFactory().createDeparture(Id.create("d", Departure.class), 0));
		TransitLine line = schedule.getFactory().createTransitLine(Id.create("l", TransitLine.class));
		line.addRoute(route);
		schedule.addTransitLine(line);
		return route;
	}

	private static List<Id<Link>> links(String... ids) {
		List<Id<Link>> list = new ArrayList<>();
		for (String id : ids) {
			list.add(Id.createLinkId(id));
		}
		return list;
	}

	private static List<Id<Link>> routeLinks(TransitRoute route) {
		List<Id<Link>> list = new ArrayList<>();
		list.add(route.getRoute().getStartLinkId());
		list.addAll(route.getRoute().getLinkIds());
		list.add(route.getRoute().getEndLinkId());
		return list;
	}
}
