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
import org.matsim.pt2matsim.config.OsmConverterConfigGroup;
import org.matsim.pt2matsim.config.PublicTransitMappingConfigGroup;
import org.matsim.pt2matsim.config.PublicTransitMappingConfigGroup.PlatformStopLink;
import org.matsim.pt2matsim.config.PublicTransitMappingStrings;
import org.matsim.pt2matsim.config.TransportModeParameterSet;
import org.matsim.pt2matsim.tools.ScheduleTools;

/**
 * Track 0-1-...-n without switches, links of 50 m, OSM stop position (platform 3) at node 1.
 */
class PlatformStopLinkExtenderTest {

	@Test
	void movesStopLinkUpToMaxDistanceAndAdaptsRoutes() {
		Network network = track(6, true);
		TransitSchedule schedule = ScheduleTools.createSchedule();
		TransitStopFacility platform = facility(schedule, "platform", "1_2");
		TransitStopFacility other = facility(schedule, "other", "5_6");
		TransitRoute passing = route(schedule, "passing", "rail", links("0_1", "1_2", "2_3", "3_4", "4_5", "5_6"), platform, other);
		TransitRoute ending = route(schedule, "ending", "rail", links("0_1", "1_2"), platform);
		TransitRoute starting = route(schedule, "starting", "rail", links("1_2", "2_3", "3_4", "4_5", "5_6"), platform, other);

		// 1_2 + 2_3 = 100 m, 3_4 would exceed 120 m
		PlatformStopLinkExtender.run(schedule, network, Set.of("rail"), 120);

		Assertions.assertEquals(Id.createLinkId("2_3"), platform.getLinkId());
		Assertions.assertEquals(Id.createLinkId("5_6"), other.getLinkId());
		Assertions.assertEquals(links("0_1", "1_2", "2_3", "3_4", "4_5", "5_6"), routeLinks(passing));
		Assertions.assertEquals(links("0_1", "1_2", "2_3"), routeLinks(ending));
		Assertions.assertEquals(links("2_3", "3_4", "4_5", "5_6"), routeLinks(starting));
	}

	@Test
	void stopsAtNextStopPosition() {
		Network network = track(6, true);
		network.getNodes().get(Id.createNodeId(3)).getAttributes()
				.putAttribute(OsmConverterConfigGroup.NODE_ATTRIBUTE_LOCAL_REF, "4");
		TransitSchedule schedule = ScheduleTools.createSchedule();
		TransitStopFacility platform = facility(schedule, "platform", "1_2");
		route(schedule, "passing", "rail", links("0_1", "1_2", "2_3", "3_4", "4_5"), platform);

		PlatformStopLinkExtender.run(schedule, network, Set.of("rail"), 500);

		Assertions.assertEquals(Id.createLinkId("2_3"), platform.getLinkId());
	}

	@Test
	void keepsStopLinkIfARouteTurnsBack() {
		Network network = track(3, true);
		TransitSchedule schedule = ScheduleTools.createSchedule();
		TransitStopFacility platform = facility(schedule, "platform", "1_2");
		TransitStopFacility back = facility(schedule, "back", "1_0");
		route(schedule, "reversing", "rail", links("0_1", "1_2", "2_1", "1_0"), platform, back);

		PlatformStopLinkExtender.run(schedule, network, Set.of("rail"), 500);

		Assertions.assertEquals(Id.createLinkId("1_2"), platform.getLinkId());
	}

	@Test
	void keepsStopLinkIfUsedByOtherMode() {
		Network network = track(6, true);
		TransitSchedule schedule = ScheduleTools.createSchedule();
		TransitStopFacility platform = facility(schedule, "platform", "1_2");
		route(schedule, "rail", "rail", links("0_1", "1_2", "2_3", "3_4"), platform);
		TransitRoute bus = route(schedule, "bus", "bus", links("0_1", "1_2"), platform);

		PlatformStopLinkExtender.run(schedule, network, Set.of("rail"), 500);

		Assertions.assertEquals(Id.createLinkId("1_2"), platform.getLinkId());
		Assertions.assertEquals(links("0_1", "1_2"), routeLinks(bus));
	}

	@Test
	void ptMapperExtendsOutLinksOnlyIfEnabled() throws Exception {
		Assertions.assertEquals(List.of(Id.createLinkId("1_2"), Id.createLinkId("5_6")), mapWithMaxExtension(0));
		Assertions.assertEquals(List.of(Id.createLinkId("2_3"), Id.createLinkId("5_6")), mapWithMaxExtension(120));
	}

	private static List<Id<Link>> mapWithMaxExtension(double maxExtension) throws Exception {
		Network network = track(6, false);
		network.getNodes().get(Id.createNodeId(5)).getAttributes()
				.putAttribute(OsmConverterConfigGroup.NODE_ATTRIBUTE_LOCAL_REF, "3");
		TransitSchedule schedule = ScheduleTools.createSchedule();
		TransitStopFacility a = facility(schedule, "A", null);
		a.setCoord(new Coord(50, 5));
		TransitStopFacility b = facility(schedule, "B", null);
		b.setCoord(new Coord(250, 5));
		for (TransitStopFacility f : List.of(a, b)) {
			f.getAttributes().putAttribute(PublicTransitMappingStrings.STOP_FACILITY_ATTRIBUTE_PLATFORM_CODE, "3");
		}
		TransitRoute route = route(schedule, "r", "rail", null, a, b);
		route.addDeparture(schedule.getFactory().createDeparture(Id.create("d", Departure.class), 8 * 3600));

		PublicTransitMappingConfigGroup config = new PublicTransitMappingConfigGroup();
		config.setNumOfThreads(1);
		config.setPlatformModes(Set.of("rail"));
		config.setPlatformStopLink(PlatformStopLink.outLink);
		config.setPlatformStopLinkMaxExtension(maxExtension);
		TransportModeParameterSet rail = new TransportModeParameterSet("rail");
		rail.setNetworkModesStr("rail");
		config.addParameterSet(rail);
		new PTMapper(schedule, network).run(config);

		TransitRoute mapped = schedule.getTransitLines().values().iterator().next().getRoutes().values().iterator().next();
		return mapped.getStops().stream().map(s -> s.getStopFacility().getLinkId()).toList();
	}

	private static Network track(int n, boolean bothDirections) {
		Network network = NetworkUtils.createNetwork();
		for (int i = 0; i <= n; i++) {
			NetworkUtils.createAndAddNode(network, Id.createNodeId(i), new Coord(i * 50, 0));
		}
		for (int i = 0; i < n; i++) {
			addLink(network, i, i + 1);
			if (bothDirections) {
				addLink(network, i + 1, i);
			}
		}
		network.getNodes().get(Id.createNodeId(1)).getAttributes()
				.putAttribute(OsmConverterConfigGroup.NODE_ATTRIBUTE_LOCAL_REF, "3");
		return network;
	}

	private static void addLink(Network network, int from, int to) {
		Node fromNode = network.getNodes().get(Id.createNodeId(from));
		Node toNode = network.getNodes().get(Id.createNodeId(to));
		Link l = NetworkUtils.createAndAddLink(network, Id.createLinkId(from + "_" + to), fromNode, toNode, 50, 30, 1000, 1);
		l.setAllowedModes(Set.of("rail"));
	}

	private static TransitStopFacility facility(TransitSchedule schedule, String id, String linkId) {
		TransitStopFacility facility = schedule.getFactory().createTransitStopFacility(
				Id.create(id, TransitStopFacility.class), new Coord(0, 0), false);
		if (linkId != null) {
			facility.setLinkId(Id.createLinkId(linkId));
		}
		schedule.addStopFacility(facility);
		return facility;
	}

	private static TransitRoute route(TransitSchedule schedule, String id, String mode, List<Id<Link>> links,
			TransitStopFacility... stops) {
		List<TransitRouteStop> routeStops = new ArrayList<>();
		for (int i = 0; i < stops.length; i++) {
			routeStops.add(schedule.getFactory().createTransitRouteStop(stops[i], i * 120, i * 120));
		}
		TransitRoute route = schedule.getFactory().createTransitRoute(Id.create(id, TransitRoute.class),
				links == null ? null : RouteUtils.createNetworkRoute(links), routeStops, mode);
		TransitLine line = schedule.getFactory().createTransitLine(Id.create(id, TransitLine.class));
		line.addRoute(route);
		schedule.addTransitLine(line);
		return route;
	}

	private static List<Id<Link>> links(String... ids) {
		List<Id<Link>> links = new ArrayList<>();
		for (String id : ids) {
			links.add(Id.createLinkId(id));
		}
		return links;
	}

	private static List<Id<Link>> routeLinks(TransitRoute route) {
		List<Id<Link>> links = new ArrayList<>();
		links.add(route.getRoute().getStartLinkId());
		links.addAll(route.getRoute().getLinkIds());
		links.add(route.getRoute().getEndLinkId());
		return links;
	}
}
