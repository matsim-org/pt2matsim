/* *********************************************************************** *
 * project: org.matsim.*
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2026 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */

package org.matsim.pt2matsim.mapping;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.population.routes.RouteUtils;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitRouteStop;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;
import org.matsim.pt2matsim.config.OsmConverterConfigGroup;
import org.matsim.pt2matsim.config.PublicTransitMappingConfigGroup;

/**
 * Moves the stop link of platform stops forward along the track, see
 * {@link PublicTransitMappingConfigGroup#getPlatformStopLinkMaxExtension()}.
 * <p>
 * Vehicles usually halt with their head at the end of the stop link. With
 * {@link PublicTransitMappingConfigGroup.PlatformStopLink#outLink}, the stop link starts at the OSM stop position. If the
 * network keeps the detailed geometry, it often ends after a few meters. This moves the stop link of stop facilities at
 * OSM stop positions forward over pass-through nodes (no stop position, no switch) until {@code maxDistance} from the
 * stop position. The network is not changed. A stop link is only moved as far as all transit routes using it follow
 * the track; if a stop facility is used by a transit route whose mode is not in {@code modes}, it is not moved. Transit
 * routes ending at a moved stop are extended, transit routes starting at a moved stop are shortened.
 */
public final class PlatformStopLinkExtender {

	private static final Logger log = LogManager.getLogger(PlatformStopLinkExtender.class);

	private PlatformStopLinkExtender() {
	}

	/**
	 * @param modes       schedule transport modes whose stop links are moved
	 * @param maxDistance maximal distance [m] from the OSM stop position to the end of the stop link
	 */
	public static void run(TransitSchedule schedule, Network network, Set<String> modes, double maxDistance) {
		Map<Id<TransitStopFacility>, List<Id<Link>>> chains = new HashMap<>();
		for (TransitStopFacility facility : schedule.getFacilities().values()) {
			Link first = network.getLinks().get(facility.getLinkId());
			if (first != null && isStopNode(first.getFromNode())) {
				List<Id<Link>> chain = chain(first, maxDistance);
				if (chain.size() > 1) {
					chains.put(facility.getId(), chain);
				}
			}
		}

		// number of links each stop link can be moved without changing the course of any transit route
		Map<Id<TransitStopFacility>, Integer> shift = new HashMap<>();
		for (TransitRoute route : routes(schedule)) {
			if (route.getRoute() == null) {
				continue;
			}
			List<Id<Link>> links = links(route);
			int[] stopIndices = stopIndices(route, links);
			for (int s = 0; s < stopIndices.length; s++) {
				TransitStopFacility facility = route.getStops().get(s).getStopFacility();
				List<Id<Link>> chain = chains.get(facility.getId());
				if (chain == null) {
					continue;
				}
				int possible = 0;
				if (!modes.contains(route.getTransportMode()) || stopIndices[s] < 0) {
					possible = 0;
				} else if (s == stopIndices.length - 1 && stopIndices[s] == links.size() - 1) {
					possible = chain.size() - 1;
				} else {
					while (possible + 1 < chain.size() && stopIndices[s] + possible + 1 < links.size()
							&& links.get(stopIndices[s] + possible + 1).equals(chain.get(possible + 1))) {
						possible++;
					}
				}
				shift.merge(facility.getId(), possible, Math::min);
			}
		}

		for (TransitRoute route : routes(schedule)) {
			if (route.getRoute() == null || route.getStops().isEmpty()) {
				continue;
			}
			List<Id<Link>> links = links(route);
			int[] stopIndices = stopIndices(route, links);
			int last = stopIndices.length - 1;
			Id<TransitStopFacility> lastFacility = route.getStops().get(last).getStopFacility().getId();
			Id<TransitStopFacility> firstFacility = route.getStops().get(0).getStopFacility().getId();
			int kLast = shift.getOrDefault(lastFacility, 0);
			int kFirst = shift.getOrDefault(firstFacility, 0);
			boolean changed = false;
			if (kLast > 0 && stopIndices[last] == links.size() - 1) {
				links.addAll(chains.get(lastFacility).subList(1, kLast + 1));
				changed = true;
			}
			if (kFirst > 0 && stopIndices[0] == 0) {
				links.subList(0, kFirst).clear();
				changed = true;
			}
			if (changed) {
				route.setRoute(RouteUtils.createNetworkRoute(links));
			}
		}

		int moved = 0;
		for (Map.Entry<Id<TransitStopFacility>, Integer> e : shift.entrySet()) {
			if (e.getValue() > 0) {
				schedule.getFacilities().get(e.getKey()).setLinkId(chains.get(e.getKey()).get(e.getValue()));
				moved++;
			}
		}
		log.info("Moved the stop link of " + moved + " of " + chains.size()
				+ " stop facilities at OSM stop positions forward along the track.");
	}

	private static List<Id<Link>> chain(Link first, double maxDistance) {
		List<Id<Link>> chain = new ArrayList<>();
		chain.add(first.getId());
		double distance = first.getLength();
		Set<Node> visited = new HashSet<>(List.of(first.getFromNode()));
		Link current = first;
		while (visited.add(current.getToNode()) && isPassThroughNode(current.getToNode())) {
			Link next = null;
			for (Link l : current.getToNode().getOutLinks().values()) {
				if (l.getToNode() != current.getFromNode()) {
					next = l;
				}
			}
			if (next == null || distance + next.getLength() > maxDistance) {
				break;
			}
			chain.add(next.getId());
			distance += next.getLength();
			current = next;
		}
		return chain;
	}

	/**
	 * No stop position and no switch: exactly two neighbours.
	 */
	private static boolean isPassThroughNode(Node node) {
		if (isStopNode(node) || node.getInLinks().size() != node.getOutLinks().size()) {
			return false;
		}
		Set<Node> neighbours = new HashSet<>();
		for (Link l : node.getInLinks().values()) {
			neighbours.add(l.getFromNode());
		}
		for (Link l : node.getOutLinks().values()) {
			neighbours.add(l.getToNode());
		}
		return neighbours.size() == 2 && !neighbours.contains(node);
	}

	private static boolean isStopNode(Node node) {
		return node.getAttributes().getAttribute(OsmConverterConfigGroup.NODE_ATTRIBUTE_LOCAL_REF) != null;
	}

	private static List<TransitRoute> routes(TransitSchedule schedule) {
		List<TransitRoute> routes = new ArrayList<>();
		for (TransitLine line : schedule.getTransitLines().values()) {
			routes.addAll(line.getRoutes().values());
		}
		return routes;
	}

	private static List<Id<Link>> links(TransitRoute route) {
		List<Id<Link>> links = new ArrayList<>();
		links.add(route.getRoute().getStartLinkId());
		links.addAll(route.getRoute().getLinkIds());
		links.add(route.getRoute().getEndLinkId());
		return links;
	}

	/**
	 * @return for each route stop the index of its stop link in {@code links}, -1 if not found
	 */
	private static int[] stopIndices(TransitRoute route, List<Id<Link>> links) {
		List<TransitRouteStop> stops = route.getStops();
		int[] indices = new int[stops.size()];
		int pointer = 0;
		for (int s = 0; s < stops.size(); s++) {
			Id<Link> linkId = stops.get(s).getStopFacility().getLinkId();
			int found = -1;
			for (int i = pointer; i < links.size(); i++) {
				if (links.get(i).equals(linkId)) {
					found = i;
					break;
				}
			}
			indices[s] = found;
			if (found >= 0) {
				pointer = found;
			}
		}
		return indices;
	}
}
