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

package org.matsim.pt2matsim.mapping.networkRouter;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.router.util.*;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.pt.transitSchedule.api.TransitRouteStop;
import org.matsim.vehicles.Vehicle;
import org.matsim.pt2matsim.config.PublicTransitMappingConfigGroup;
import org.matsim.pt2matsim.mapping.linkCandidateCreation.LinkCandidate;

import java.util.HashMap;
import java.util.Map;

/**
 * A LeastCostPathCalculator using FastAStarLandmarks.
 *
 * @author polettif
 */
public class FastAStarRouter implements Router {

	private final Network network;

	private final LeastCostPathCalculator pathCalculator;
	private final Map<Tuple<Id<Node>, Id<Node>>, LeastCostPathCalculator.Path> paths;
	private static PublicTransitMappingConfigGroup.TravelCostType travelCostType = PublicTransitMappingConfigGroup.TravelCostType.linkLength;


	public static void setTravelCostType(PublicTransitMappingConfigGroup.TravelCostType type) {
		travelCostType = type;
	}

	public FastAStarRouter(Network network) {
		this.paths = new HashMap<>();
		this.network = network;

		LeastCostPathCalculatorFactory factory = new FastAStarLandmarksFactory(network, this);
		this.pathCalculator = factory.createPathCalculator(network, this, this);
	}

	/**
	 * Synchronized since {@link org.matsim.core.router.Dijkstra} is not thread safe.
	 */
	@Override
	public synchronized LeastCostPathCalculator.Path calcLeastCostPath(Id<Node> fromNode, Id<Node> toNode) {
		if(fromNode != null && toNode != null) {

			Tuple<Id<Node>, Id<Node>> nodes = new Tuple<>(fromNode, toNode);
			if(!paths.containsKey(nodes)) {
				Node nodeA = network.getNodes().get(fromNode);
				Node nodeB = network.getNodes().get(toNode);
				paths.put(nodes, pathCalculator.calcLeastCostPath(nodeA, nodeB, 0.0, null, null));
			}
			return paths.get(nodes);
		} else {
			return null;
		}
	}

	@Override
	public double getMinimalTravelCost(TransitRouteStop fromStop, TransitRouteStop toStop) {
		double travelTime = (toStop.getArrivalOffset() - fromStop.getDepartureOffset());
		double beelineDistance = CoordUtils.calcEuclideanDistance(fromStop.getStopFacility().getCoord(), toStop.getStopFacility().getCoord());

		if(travelCostType.equals(PublicTransitMappingConfigGroup.TravelCostType.travelTime)) {
			return travelTime;
		} else {
			return beelineDistance;
		}
	}

	@Override
	public double getArtificialLinkFreeSpeed(double maxAllowedTravelCost, LinkCandidate fromLinkCandidate, LinkCandidate toLinkCandidate) {
		return 1;
		/* Varying freespeeds do not work with maxAllowedTravelcost == 0.
		if(travelCostType.equals(PublicTransitMappingConfigGroup.TravelCostType.travelTime)) {
			double linkLength = CoordUtils.calcEuclideanDistance(fromLinkCandidate.getToNodeCoord(), toLinkCandidate.getFromNodeCoord());
			return linkLength / maxAllowedTravelCost;
		} else {
			return 1;
		}
		*/
	}

	@Override
	public double getArtificialLinkLength(double maxAllowedTravelCost, LinkCandidate fromLinkCandidate, LinkCandidate toLinkCandidate) {
		if(travelCostType.equals(PublicTransitMappingConfigGroup.TravelCostType.travelTime)) {
			return CoordUtils.calcEuclideanDistance(fromLinkCandidate.getToNodeCoord(), toLinkCandidate.getFromNodeCoord());
		} else {
			return maxAllowedTravelCost;
		}
	}

	// LeastCostPathCalculator methods
	@Override
	public double getLinkTravelDisutility(Link link, double time, Person person, Vehicle vehicle) {
		return this.getLinkMinimumTravelDisutility(link);
	}

	@Override
	public double getLinkMinimumTravelDisutility(Link link) {
		return (travelCostType.equals(PublicTransitMappingConfigGroup.TravelCostType.travelTime) ? link.getLength() / link.getFreespeed() : link.getLength());
//		double travelCost = (travelCostType.equals(PublicTransitMappingConfigGroup.TravelCostType.travelTime) ? link.getLength() / link.getFreespeed() : link.getLength());
//		if(link.getToNode().getId().equals(uTurnFromNodeId) && link.getFromNode().getId().equals(uTurnToNodeId)) {
//			return uTurnCost + travelCost;
//		} else {
//			return travelCost;
//		}
	}

	@Override
	public double getLinkTravelTime(Link link, double time, Person person, Vehicle vehicle) {
		return link.getLength() / link.getFreespeed();
	}
}