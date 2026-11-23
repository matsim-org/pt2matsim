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
import java.util.List;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.population.routes.NetworkRoute;
import org.matsim.core.population.routes.RouteUtils;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitRouteStop;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;
import org.matsim.pt2matsim.config.PublicTransitMappingConfigGroup;
import org.matsim.pt2matsim.tools.ScheduleTools;

/**
 * Removes the reversal before the last stop of a transit route, see
 * {@link PublicTransitMappingConfigGroup#getPlatformRemoveTerminalReversal()}.
 * <p>
 * The pseudo routing does not penalize reversing. At the last stop, the transit route ends on the stop link, so the
 * shorter stop link in the opposite direction may be chosen: the vehicle passes the stop, reverses on the opposite
 * link and halts. If the opposite link of the last stop link is part of the transit route (after the previous stop),
 * the transit route is cut after the opposite link and the last stop is moved to a child stop facility on the
 * opposite link. The network is not changed.
 */
public final class TerminalStopReversalRemover {

	private static final Logger log = LogManager.getLogger(TerminalStopReversalRemover.class);

	private TerminalStopReversalRemover() {
	}

	/**
	 * @param modes schedule transport modes whose transit routes are adapted
	 * @return number of adapted transit routes
	 */
	public static int run(TransitSchedule schedule, Network network, Set<String> modes) {
		int fixed = 0;
		for (TransitLine line : schedule.getTransitLines().values()) {
			for (TransitRoute route : line.getRoutes().values()) {
				if (modes.contains(route.getTransportMode()) && removeReversal(schedule, network, route)) {
					fixed++;
				}
			}
		}
		log.info("Removed the reversal before the last stop of " + fixed + " transit routes.");
		return fixed;
	}

	private static boolean removeReversal(TransitSchedule schedule, Network network, TransitRoute route) {
		List<TransitRouteStop> stops = route.getStops();
		NetworkRoute networkRoute = route.getRoute();
		if (stops.size() < 2 || networkRoute == null) {
			return false;
		}
		List<Id<Link>> links = new ArrayList<>();
		links.add(networkRoute.getStartLinkId());
		links.addAll(networkRoute.getLinkIds());
		links.add(networkRoute.getEndLinkId());

		TransitRouteStop lastStop = stops.get(stops.size() - 1);
		Id<Link> stopLinkId = lastStop.getStopFacility().getLinkId();
		Link stopLink = network.getLinks().get(stopLinkId);
		if (stopLink == null || !stopLinkId.equals(links.get(links.size() - 1))) {
			return false;
		}
		Link opposite = NetworkUtils.findLinkInOppositeDirection(stopLink);
		if (opposite == null) {
			return false;
		}
		int cut = links.subList(0, links.size() - 1).lastIndexOf(opposite.getId());
		int previousStop = links.subList(0, links.size() - 1).lastIndexOf(stops.get(stops.size() - 2).getStopFacility().getLinkId());
		if (cut < 0 || cut <= previousStop) {
			return false;
		}

		route.setRoute(RouteUtils.createNetworkRoute(new ArrayList<>(links.subList(0, cut + 1))));
		lastStop.setStopFacility(facilityOnLink(schedule, lastStop.getStopFacility(), opposite.getId()));
		return true;
	}

	private static TransitStopFacility facilityOnLink(TransitSchedule schedule, TransitStopFacility facility, Id<Link> linkId) {
		String parentId = ScheduleTools.getParentStopFacilityId(facility.getId().toString());
		Id<TransitStopFacility> id = ScheduleTools.createChildStopFacilityId(parentId, linkId.toString());
		TransitStopFacility existing = schedule.getFacilities().get(id);
		if (existing != null) {
			return existing;
		}
		TransitStopFacility child = schedule.getFactory().createTransitStopFacility(id, facility.getCoord(), facility.getIsBlockingLane());
		child.setName(facility.getName());
		child.setStopAreaId(facility.getStopAreaId());
		child.setLinkId(linkId);
		facility.getAttributes().getAsMap().forEach((k, v) -> child.getAttributes().putAttribute(k, v));
		schedule.addStopFacility(child);
		return child;
	}
}
