/* *********************************************************************** *
 * project: org.matsim.*
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2016 by the members listed in the COPYING,        *
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

package org.matsim.pt2matsim.mapping.pseudoRouter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.pt.transitSchedule.api.MinimalTransferTimes.MinimalTransferTimesIterator;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitRouteStop;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitScheduleFactory;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;
import org.matsim.pt2matsim.tools.ScheduleTools;

/**
 * @author polettif
 */
public class PseudoScheduleImpl implements PseudoSchedule {

	private final Set<PseudoTransitRoute> pseudoSchedule = new HashSet<>();

	@Override
	public void addPseudoRoute(TransitLine transitLine, TransitRoute transitRoute,
			List<PseudoRouteStop> pseudoStopSequence, List<Id<Link>> networkLinkList) {
		pseudoSchedule.add(new PseudoTransitRouteImpl(transitLine, transitRoute, pseudoStopSequence, networkLinkList));
	}

	@Override
	public Set<PseudoTransitRoute> getPseudoRoutes() {
		return pseudoSchedule;
	}

	@Override
	public void mergePseudoSchedule(PseudoSchedule otherPseudoSchedule) {
		pseudoSchedule.addAll(otherPseudoSchedule.getPseudoRoutes());
	}

	@Override
	public void createFacilitiesAndLinkSequences(TransitSchedule schedule) {
		TransitScheduleFactory scheduleFactory = schedule.getFactory();

		Map<Id<TransitStopFacility>, Set<Id<TransitStopFacility>>> parentsToChildren = new HashMap<>();

		Logger logger = LogManager.getLogger(PseudoScheduleImpl.class);

		int totalNumber = pseudoSchedule.size();
		int currentNumber = 0;
		double lastUpdate = Double.NEGATIVE_INFINITY;

		for (PseudoTransitRoute pseudoTransitRoute : pseudoSchedule) {
			List<PseudoRouteStop> pseudoStopSequence = pseudoTransitRoute.getPseudoStops();
			List<TransitRouteStop> newStopSequence = new ArrayList<>();

			for (PseudoRouteStop pseudoStop : pseudoStopSequence) {
				Id<TransitStopFacility> childStopFacilityId = ScheduleTools
						.createChildStopFacilityId(pseudoStop.getParentStopFacilityId(), pseudoStop.getLinkId());

				// if child stop facility for this link has not yet been generated
				if (!schedule.getFacilities().containsKey(childStopFacilityId)) {
					TransitStopFacility newFacility = scheduleFactory.createTransitStopFacility(
							Id.create(childStopFacilityId, TransitStopFacility.class), pseudoStop.getCoord(),
							pseudoStop.isBlockingLane());
					newFacility.setLinkId(pseudoStop.getLinkId());
					newFacility.setName(pseudoStop.getFacilityName());
					newFacility.setStopAreaId(pseudoStop.getStopAreaId());
					schedule.addStopFacility(newFacility);
				}

				// create new TransitRouteStop and add it to the newStopSequence
				TransitRouteStop newTransitRouteStop = scheduleFactory.createTransitRouteStop(
						schedule.getFacilities().get(childStopFacilityId), pseudoStop.getArrivalOffset().seconds(),
						pseudoStop.getDepartureOffset().seconds());
				newTransitRouteStop.setAwaitDepartureTime(pseudoStop.awaitsDepartureTime());
				newStopSequence.add(newTransitRouteStop);

				parentsToChildren.computeIfAbsent(pseudoStop.getParentStopFacilityId(), id -> new HashSet<>());
				parentsToChildren.get(pseudoStop.getParentStopFacilityId()).add(childStopFacilityId);
			}

			// create a new transitRoute
			TransitRoute newTransitRoute = scheduleFactory.createTransitRoute(
					pseudoTransitRoute.getTransitRoute().getId(), null, newStopSequence,
					pseudoTransitRoute.getTransitRoute().getTransportMode());

			// add departures
			pseudoTransitRoute.getTransitRoute().getDepartures().values().forEach(newTransitRoute::addDeparture);

			// add link sequence
			List<Id<Link>> l = pseudoTransitRoute.getNetworkLinkIdList();
			newTransitRoute.setRoute(new LinkSequence(l.get(0), l.subList(1, l.size() - 1), l.get(l.size() - 1)));

			// add description
			newTransitRoute.setDescription(pseudoTransitRoute.getTransitRoute().getDescription());

			// remove the old route
			schedule.getTransitLines().get(pseudoTransitRoute.getTransitLineId())
					.removeRoute(pseudoTransitRoute.getTransitRoute());

			// add new route to container
			schedule.getTransitLines().get(pseudoTransitRoute.getTransitLineId()).addRoute(newTransitRoute);
			// newRoutes.add(new Tuple<>(pseudoTransitRoute.getTransitLineId(), newRoute));

			// Recover minimal transfer times between child stop facilities from parent stop
			// facilities
			MinimalTransferTimesIterator iterator = schedule.getMinimalTransferTimes().iterator();

			while (iterator.hasNext()) {
				iterator.next();

				Id<TransitStopFacility> fromId = iterator.getFromStopId();
				Id<TransitStopFacility> toId = iterator.getToStopId();

				if (parentsToChildren.containsKey(fromId) && parentsToChildren.containsKey(toId)) {
					for (Id<TransitStopFacility> childFromId : parentsToChildren.get(fromId)) {
						for (Id<TransitStopFacility> childToId : parentsToChildren.get(toId)) {
							schedule.getMinimalTransferTimes().set(childFromId, childToId, iterator.getSeconds());
						}
					}
				}
			}

			currentNumber++;
			if (System.currentTimeMillis() >= lastUpdate + 1000.0 || currentNumber == totalNumber) {
				lastUpdate = System.currentTimeMillis();
				logger.info(String.format("PseudoScheduleImpl::createFacilitiesAndLinkSequences %d/%d (%.2f%%)",
						currentNumber, totalNumber, 100.0 * currentNumber / totalNumber));
			}
		}
	}
}
