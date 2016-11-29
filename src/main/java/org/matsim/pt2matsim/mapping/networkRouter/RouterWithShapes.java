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

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.router.util.*;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitRouteStop;
import org.matsim.pt2matsim.config.PublicTransitMappingConfigGroup;
import org.matsim.pt2matsim.gtfs.lib.Shape;
import org.matsim.pt2matsim.mapping.linkCandidateCreation.LinkCandidate;
import org.matsim.pt2matsim.tools.ShapeTools;
import org.matsim.pt2matsim.tools.NetworkTools;
import org.matsim.vehicles.Vehicle;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * A LeastCostPathCalculator using FastAStarEuclidian. One Router should be initialized
 * for each GTFS shape.
 *
 * @author polettif
 */
public class RouterWithShapes implements Router {

	private final Network network;

	private final LeastCostPathCalculator pathCalculator;
	private final Map<Tuple<Id<Node>, Id<Node>>, LeastCostPathCalculator.Path> paths;
	private static PublicTransitMappingConfigGroup.TravelCostType travelCostType = PublicTransitMappingConfigGroup.TravelCostType.linkLength;
	private static Map<String, Set<String>> modeRoutingAssignment;

	private Shape shape;

	public static void setTravelCostType(PublicTransitMappingConfigGroup.TravelCostType type) {
		travelCostType = type;
	}

	public RouterWithShapes(Network network, Set<String> networkTransportModes, Shape shape) {
		Network filteredNetwork = NetworkTools.filterNetworkByLinkMode(network, networkTransportModes);
		Coord[] extent = shape.getExtent();
		double dx = extent[1].getX() - extent[0].getX();
		double dy = extent[1].getY() - extent[0].getY();

		if(dx < 0 || dy < 0) {
			throw new RuntimeException("Coordinate system error!");
		}

		Coord sw = new Coord(extent[0].getX()-dx, extent[0].getY()-dy);
		Coord ne = new Coord(extent[1].getX()+dx, extent[1].getY()+dy);
		NetworkTools.cutNetwork(filteredNetwork, sw, ne);
		this.network = filteredNetwork;

		this.paths = new HashMap<>();
		this.shape = shape;

		LeastCostPathCalculatorFactory factory = new FastAStarEuclideanFactory(network, this);
		this.pathCalculator = factory.createPathCalculator(network, this, this);
	}

	public static void setModeRoutingAssignment(Map<String, Set<String>> modeRoutingAssignment) {
		RouterWithShapes.modeRoutingAssignment = modeRoutingAssignment;
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
	public Network getNetwork() {
		return network;
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

	@Override
	public double getLinkTravelCost(Link link) {
		return this.getLinkMinimumTravelDisutility(link);
	}


	// LeastCostPathCalculator methods
	@Override
	public double getLinkTravelDisutility(Link link, double time, Person person, Vehicle vehicle) {
		return this.getLinkMinimumTravelDisutility(link);
	}

	@Override
	public double getLinkMinimumTravelDisutility(Link link) {
		// return (travelCostType.equals(PublicTransitMappingConfigGroup.TravelCostType.travelTime) ? link.getLength() / link.getFreespeed() : link.getLength());
//		if(link.getToNode().getId().equals(uTurnFromNodeId) && link.getFromNode().getId().equals(uTurnToNodeId)) {
//			travelCost += uTurnCost;
// 		}

		double travelCost = (travelCostType.equals(PublicTransitMappingConfigGroup.TravelCostType.travelTime) ? link.getLength() / link.getFreespeed() : link.getLength());

		/**
		 * Change travel costs based on distance to path
		 */

		if(shape != null) {
			double dist = ShapeTools.calcMinDistanceToShape(link, shape);
			if(dist < 20 && dist > 0) {
				travelCost *= 0.8;
			}
		}

		return travelCost;
	}

	@Override
	public double getLinkTravelTime(Link link, double time, Person person, Vehicle vehicle) {
		return link.getLength() / link.getFreespeed();
	}

}