/*
 * *********************************************************************** *
 * project: org.matsim.*                                                   *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2015 by the members listed in the COPYING,        *
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
 * *********************************************************************** *
 */

package org.matsim.pt2matsim.mapping.networkRouter;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.router.util.LeastCostPathCalculator;
import org.matsim.core.router.util.TravelDisutility;
import org.matsim.core.router.util.TravelTime;
import org.matsim.pt.transitSchedule.api.TransitRouteStop;
import org.matsim.pt2matsim.mapping.PseudoRouting;
import org.matsim.pt2matsim.mapping.linkCandidateCreation.LinkCandidate;

/**
 * A Router that calculates the least cost path on a network.
 *
 * @author polettif
 *
 */
public interface Router extends TravelDisutility, TravelTime {

	LeastCostPathCalculator.Path calcLeastCostPath(Id<Node> toNode, Id<Node> fromNode);

	/**
	 * @return The minimal travel cost between two TransitRouteStops
	 */
	double getMinimalTravelCost(TransitRouteStop fromStop, TransitRouteStop toStop);

	/**
	 * If {@link PseudoRouting} needs to add an artificial link to the network, this method returns
	 * the freespeed value.
	 */
	double getArtificialLinkFreeSpeed(double maxAllowedTravelCost, LinkCandidate fromLinkCandidate, LinkCandidate toLinkCandidate);

	/**
	 * If {@link PseudoRouting} needs to add an artificial link to the network, this method returns
	 * the link length.
	 */
	double getArtificialLinkLength(double maxAllowedTravelCost, LinkCandidate linkCandidateCurrent, LinkCandidate linkCandidateNext);
}
