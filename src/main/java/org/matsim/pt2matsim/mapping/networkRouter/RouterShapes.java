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
import org.matsim.core.router.util.FastAStarEuclideanFactory;
import org.matsim.core.router.util.LeastCostPathCalculator;
import org.matsim.core.router.util.LeastCostPathCalculatorFactory;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.pt.transitSchedule.api.TransitRouteStop;
import org.matsim.pt2matsim.config.PublicTransitMappingConfigGroup;
import org.matsim.pt2matsim.lib.RouteShape;
import org.matsim.pt2matsim.mapping.linkCandidateCreation.LinkCandidate;
import org.matsim.pt2matsim.tools.NetworkTools;
import org.matsim.pt2matsim.tools.ShapeTools;
import org.matsim.vehicles.Vehicle;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * A LeastCostPathCalculator using FastAStarEuclidian. One Router should be initialized
 * for each GTFS shape. A link's travel cost is modified depending on its distance to the shape.
 *
 * @author polettif
 */
public class RouterShapes implements Router {

	private static PublicTransitMappingConfigGroup.TravelCostType travelCostType = PublicTransitMappingConfigGroup.TravelCostType.linkLength;
	private static double maxWeightDistance = 30;
	private static double cutBuffer = 1000;
	private final Network network;
	private final LeastCostPathCalculator pathCalculator;
	private final RouteShape shape;
	private final Map<Tuple<Id<Node>, Id<Node>>, LeastCostPathCalculator.Path> paths;


	public RouterShapes(Network paramNetwork, Set<String> networkTransportModes, RouteShape shape) {
		this.shape = shape;

		// todo this setup could be improved
		this.network = NetworkTools.createFilteredNetworkByLinkMode(paramNetwork, networkTransportModes);
		Collection<Node> nodesWithinBuffer = ShapeTools.getNodesWithinBuffer(this.network, shape, cutBuffer);
		NetworkTools.cutNetwork(network, nodesWithinBuffer);


		this.paths = new HashMap<>();

		LeastCostPathCalculatorFactory factory = new FastAStarEuclideanFactory(this.network, this);
		this.pathCalculator = factory.createPathCalculator(this.network, this, this);
	}

	public static void setTravelCostType(PublicTransitMappingConfigGroup.TravelCostType type) {
		travelCostType = type;
	}

	public static void setMaxWeightDistance(double distance) {
		maxWeightDistance = distance;
	}

	public static void setNetworkCutBuffer(double buffer) {
		cutBuffer = buffer;
	}

	/**
	 * Calculates the travel cost and change it based on distance to path
	 */
	private double calcLinkTravelCost(Link link) {
		double travelCost = (travelCostType.equals(PublicTransitMappingConfigGroup.TravelCostType.travelTime) ? link.getLength() / link.getFreespeed() : link.getLength());

		if(shape != null) {
			double dist = ShapeTools.calcMinDistanceToShape(link, shape);
			double factor = dist / maxWeightDistance + 0.1;
			if(factor > 1) factor = 3;
			travelCost *= factor;
		}
		return travelCost;
	}

	/**
	 * Synchronized since {@link org.matsim.core.router.Dijkstra} is not thread safe.
	 */
	@Override
	public synchronized LeastCostPathCalculator.Path calcLeastCostPath(Id<Node> fromNodeId, Id<Node> toNodeId) {
		Node nodeA = this.network.getNodes().get(fromNodeId);
		Node nodeB = this.network.getNodes().get(toNodeId);

		if(nodeA != null && nodeB != null) {
			Tuple<Id<Node>, Id<Node>> nodes = new Tuple<>(fromNodeId, toNodeId);
			if(!paths.containsKey(nodes)) {
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
		return this.calcLinkTravelCost(link);
	}

	@Override
	public double getLinkMinimumTravelDisutility(Link link) {
		return this.calcLinkTravelCost(link);
	}

	@Override
	public double getLinkTravelTime(Link link, double time, Person person, Vehicle vehicle) {
		return link.getLength() / link.getFreespeed();
	}

}