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

package org.matsim.pt2matsim.tools;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.population.routes.RouteUtils;
import org.matsim.core.router.util.LeastCostPathCalculator;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.pt.transitSchedule.api.*;
import org.matsim.pt2matsim.config.PublicTransitMappingConfigGroup;
import org.matsim.pt2matsim.config.PublicTransitMappingStrings;
import org.matsim.pt2matsim.mapping.linkCandidateCreation.LinkCandidate;
import org.matsim.pt2matsim.mapping.pseudoRouter.ArtificialLinkImpl;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Provides some static tools for PTMapper.
 *
 * @author polettif
 */
public final class PTMapperTools {

	protected static Logger log = Logger.getLogger(PTMapperTools.class);

	private PTMapperTools() {
	}

	/**
	 * @return the linkIds of the links in path
	 */
	public static List<Id<Link>> getLinkIdsFromPath(LeastCostPathCalculator.Path path) {
		return path.links.stream().map(Link::getId).collect(Collectors.toList());
	}

	/**
	 * Assigns links that are present in both sets to the set belonging to the closer coordinate.
	 *
	 * @return true if sets have been modified
	 */
	public static boolean separateLinks(Coord coordA, Set<Link> linkSetA, Coord coordB, Set<Link> linkSetB) {
		Set<Link> removeFromA = new HashSet<>();
		Set<Link> removeFromB = new HashSet<>();

		for(Link linkA : linkSetA) {
			for(Link linkB : linkSetB) {
				if(linkA.getId().equals(linkB.getId())) {

					if(!linkA.getFromNode().equals(linkB.getFromNode())) {
						throw new IllegalArgumentException("10000");
					}

					double distA = CoordUtils.distancePointLinesegment(linkA.getFromNode().getCoord(), linkA.getToNode().getCoord(), coordA);
					double distB = CoordUtils.distancePointLinesegment(linkA.getFromNode().getCoord(), linkA.getToNode().getCoord(), coordB);
					if(distA > distB) {
						removeFromA.add(linkA);
					} else {
						removeFromB.add(linkB);
					}
				}
			}
		}
		removeFromA.forEach(linkSetA::remove);
		removeFromB.forEach(linkSetB::remove);

		return (removeFromA.size() > 0 && removeFromB.size() > 0);
	}


	/**
	 * Checks for each child stop facility if the link before or after is closer to the facility
	 * than its referenced link. If so, the child stop facility is replaced with the one closer
	 * to the facility coordinates. Transit routes with loop route profiles (i.e. a stop is accessed
	 * twice in a stop sequence) are ignored.
	 *
	 * @return the number of child stop facilities pulled
	 */
	public static int pullChildStopFacilitiesTogether(TransitSchedule schedule, Network network) {
		int nPulled = 0;
		for(TransitLine line : schedule.getTransitLines().values()) {
			for(TransitRoute transitRoute : line.getRoutes().values()) {
				boolean hasStopLoop = ScheduleTools.routeHasStopSequenceLoop(transitRoute);
				if(transitRoute.getRoute() != null && !hasStopLoop) {
					TransitRouteStop currentStop;
					List<TransitRouteStop> routeStops = transitRoute.getStops();

					Iterator<TransitRouteStop> stopsIterator = routeStops.iterator();

					List<Id<Link>> linkIdList = ScheduleTools.getTransitRouteLinkIds(transitRoute);
					List<Link> linkList = NetworkTools.getLinksFromIds(network, linkIdList);

					currentStop = stopsIterator.next();

					// look for a closer link before the route's start
					// only use links with closer fromNodes
					Set<Link> inlinksWithSameMode = NetworkTools.filterLinkSetExactlyByModes(linkList.get(0).getFromNode().getInLinks().values(), linkList.get(0).getAllowedModes());
					double firstDist = CoordUtils.calcEuclideanDistance(currentStop.getStopFacility().getCoord(), linkList.get(0).getFromNode().getCoord());
					for(Link l : new HashSet<>(inlinksWithSameMode)) {
						if(CoordUtils.calcEuclideanDistance(l.getFromNode().getCoord(), currentStop.getStopFacility().getCoord()) > firstDist) {
							inlinksWithSameMode.remove(l);
						}
					}
					Id<Link> closerLinkBefore = useCloserRefLinkForChildStopFacility(schedule, network, transitRoute, currentStop.getStopFacility(), inlinksWithSameMode);
					if(closerLinkBefore != null) {
						linkIdList.add(0, closerLinkBefore);
						nPulled++;
					}
					currentStop = stopsIterator.next();

					// optimize referenced links between start and end
					for(int i = 1; i < linkList.size() - 1; i++) {
						if(linkList.get(i).getId().equals(currentStop.getStopFacility().getLinkId())) {
							Set<Link> testSet = new HashSet<>();
							if(!(linkList.get(i) instanceof ArtificialLinkImpl)) testSet.add(linkList.get(i));
							if(!(linkList.get(i - 1) instanceof ArtificialLinkImpl)) testSet.add(linkList.get(i - 1));
							if(!(linkList.get(i + 1) instanceof ArtificialLinkImpl)) testSet.add(linkList.get(i + 1));

							Id<Link> check = useCloserRefLinkForChildStopFacility(schedule, network, transitRoute, currentStop.getStopFacility(), testSet);

							if(check != null) nPulled++;

							if(stopsIterator.hasNext()) {
								currentStop = stopsIterator.next();
							}
						}
					}

					// look for a closer link after the route's end
					currentStop = routeStops.get(routeStops.size() - 1);
					Set<Link> outlinksWithSameMode = NetworkTools.filterLinkSetExactlyByModes(linkList.get(linkList.size() - 1).getToNode().getOutLinks().values(), linkList.get(linkList.size() - 1).getAllowedModes());
					Id<Link> closerLinkAfter = useCloserRefLinkForChildStopFacility(schedule, network, transitRoute, currentStop.getStopFacility(), outlinksWithSameMode);
					if(closerLinkAfter != null) {
						linkIdList.add(closerLinkAfter);
						nPulled++;
					}

					// set the new link list
					transitRoute.setRoute(RouteUtils.createNetworkRoute(linkIdList, network));
				}
			}
		}
		return nPulled;
	}


	/**
	 * If a link of <tt>comparingLinks</tt> is closer to the stop facility than
	 * its currently referenced link, the closest link is used.
	 *
	 * @return The id of the new closest link or <tt>null</tt> if the existing ref link
	 * was used.
	 */
	private static Id<Link> useCloserRefLinkForChildStopFacility(TransitSchedule schedule, Network network, TransitRoute transitRoute, TransitStopFacility stopFacility, Collection<? extends Link> comparingLinks) {
		// check if previous link is closer to stop facility
		double minDist = CoordTools.distanceStopFacilityToLink(stopFacility, network.getLinks().get(stopFacility.getLinkId()));
		Link minLink = null;

		for(Link comparingLink : comparingLinks) {
			double distCompare = CoordTools.distanceStopFacilityToLink(stopFacility, comparingLink);
			if(distCompare < minDist) {
				minDist = distCompare;
				minLink = comparingLink;
			}
		}

		if(minLink != null) {
			TransitStopFacility newChildStopFacility;
			Id<TransitStopFacility> parentId = ScheduleTools.createParentStopFacilityId(stopFacility);
			Id<TransitStopFacility> newChildStopFacilityId = ScheduleTools.createChildStopFacilityId(parentId, minLink.getId());
			if(schedule.getFacilities().containsKey(newChildStopFacilityId)) {
				newChildStopFacility = schedule.getFacilities().get(newChildStopFacilityId);
			} else {
				newChildStopFacility = schedule.getFactory().createTransitStopFacility(newChildStopFacilityId, stopFacility.getCoord(), false);
				newChildStopFacility.setName(stopFacility.getName());
				newChildStopFacility.setStopAreaId(stopFacility.getStopAreaId());
				newChildStopFacility.setLinkId(minLink.getId());
				schedule.addStopFacility(newChildStopFacility);
			}
			transitRoute.getStop(stopFacility).setStopFacility(newChildStopFacility);
			return minLink.getId();
		} else {
			return null;
		}
	}

	public static void setLogLevels() {
		Logger.getLogger(org.matsim.core.router.Dijkstra.class).setLevel(Level.ERROR); // suppress no route found warnings
		Logger.getLogger(Network.class).setLevel(Level.WARN);
		Logger.getLogger(org.matsim.core.network.filter.NetworkFilterManager.class).setLevel(Level.WARN);
		Logger.getLogger(org.matsim.core.router.util.PreProcessDijkstra.class).setLevel(Level.WARN);
		Logger.getLogger(org.matsim.core.router.util.PreProcessDijkstra.class).setLevel(Level.WARN);
		Logger.getLogger(org.matsim.core.router.util.PreProcessEuclidean.class).setLevel(Level.WARN);
		Logger.getLogger(org.matsim.core.router.util.PreProcessLandmarks.class).setLevel(Level.WARN);
	}

	/**
	 * Calculates the travel cost (distance or time based on travelCostType)
	 */
	public static double calcMinTravelCost(TransitRouteStop fromTransitRouteStop, TransitRouteStop toTransitRouteStop, PublicTransitMappingConfigGroup.TravelCostType travelCostType) {
		double travelTime = (toTransitRouteStop.getArrivalOffset() - fromTransitRouteStop.getDepartureOffset());
		double beelineDistance = CoordUtils.calcEuclideanDistance(fromTransitRouteStop.getStopFacility().getCoord(), toTransitRouteStop.getStopFacility().getCoord());

		if(travelCostType.equals(PublicTransitMappingConfigGroup.TravelCostType.travelTime)) {
			return travelTime;
		} else {
			return beelineDistance;
		}
	}

	public static double calcTravelCost(Link link, PublicTransitMappingConfigGroup.TravelCostType travelCostType) {
		return (travelCostType.equals(PublicTransitMappingConfigGroup.TravelCostType.travelTime) ? link.getLength() / link.getFreespeed() : link.getLength());
	}

	public static Id<Link> createArtificialLinkId(LinkCandidate fromLinkCandidate, LinkCandidate toLinkCandidate) {
		if(fromLinkCandidate.isLoopLink()) {
			return Id.createLinkId(PublicTransitMappingStrings.PREFIX_ARTIFICIAL + fromLinkCandidate.getStop().getStopFacility().getId() + "_" + toLinkCandidate.getLink().getId());
		} else if(toLinkCandidate.isLoopLink()) {
			return Id.createLinkId(PublicTransitMappingStrings.PREFIX_ARTIFICIAL + fromLinkCandidate.getLink().getId() + "_" + toLinkCandidate.getStop().getStopFacility().getId());
		} else {
			return Id.createLinkId(PublicTransitMappingStrings.PREFIX_ARTIFICIAL + fromLinkCandidate.getLink().getId() + "_" + toLinkCandidate.getLink().getId());
		}
	}

	public static Id<Link> createArtificialLinkId(TransitStopFacility stopFacility) {
		return Id.createLinkId(PublicTransitMappingStrings.PREFIX_ARTIFICIAL + stopFacility.getId());
	}

	/**
	 * Creates a node and dummy/loop link on the coordinate of the stop facility and
	 * adds both to the network. The stop facility is NOT referenced.
	 *
	 * @return the new link or the existing link if it's already present in the network
	 */
	public static Link createArtificialStopFacilityLink(TransitStopFacility stopFacility, Network network, String prefix, double freespeed, Set<String> transportModes) {
		Id<Link> dummyLinkId = createArtificialLinkId(stopFacility);

		Link dummyLink = network.getLinks().get(dummyLinkId);
		if(dummyLink != null) {
			return dummyLink;
		} else {
			Node dummyNode = NetworkUtils.createNode(Id.createNodeId(prefix + stopFacility.getId()), stopFacility.getCoord());
			network.addNode(dummyNode);
			dummyLink = NetworkUtils.createLink(dummyLinkId, dummyNode, dummyNode, network, 10, freespeed, 9999, 1);
			dummyLink.setAllowedModes(transportModes);
			network.addLink(dummyLink);

			return dummyLink;
		}
	}

	/**
	 * Adds transfers for all child stop facilities
	 */
	public static void addTransfersForChildStopFacilities(TransitSchedule schedule) {
		Map<Id<TransitStopFacility>, Set<TransitStopFacility>> parentChilds = new HashMap<>();

		for(TransitStopFacility transitStopFacility : schedule.getFacilities().values()) {
			Set<TransitStopFacility> childSet = parentChilds.computeIfAbsent(ScheduleTools.createParentStopFacilityId(transitStopFacility), k -> new HashSet<>());
			childSet.add(transitStopFacility);
		}

		MinimalTransferTimes mininmalTransferTimes = schedule.getMinimalTransferTimes();
		for(Set<TransitStopFacility> entry : parentChilds.values()) {
			for(TransitStopFacility from : entry) {
				for(TransitStopFacility to : entry) {
					if(!from.equals(to)) {
						mininmalTransferTimes.set(from.getId(), to.getId(), 0);
					}
				}
			}
		}
	}
}