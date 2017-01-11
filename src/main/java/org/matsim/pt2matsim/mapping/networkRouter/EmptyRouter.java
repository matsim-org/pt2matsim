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
import org.matsim.api.core.v01.network.Node;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.router.util.LeastCostPathCalculator;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.pt.transitSchedule.api.TransitRouteStop;
import org.matsim.pt2matsim.mapping.linkCandidateCreation.LinkCandidate;
import org.matsim.vehicles.Vehicle;

/**
 * Dummy class that always returns <tt>null</tt> as least cost paths.
 * Can be used for transitRoutes that are always mapped artificially in
 * {@link org.matsim.pt2matsim.mapping.PseudoRouting}
 *
 * @author polettif
 */
public class EmptyRouter implements Router {

	@Override
	public LeastCostPathCalculator.Path calcLeastCostPath(Id<Node> toNode, Id<Node> fromNode) {
		return null;
	}

	@Override
	public double getMinimalTravelCost(TransitRouteStop fromStop, TransitRouteStop toStop) {
		return Double.MAX_VALUE;
	}

	@Override
	public double getArtificialLinkFreeSpeed(double maxAllowedTravelCost, LinkCandidate fromLinkCandidate, LinkCandidate toLinkCandidate) {
		return 1;
	}

	@Override
	public double getArtificialLinkLength(double maxAllowedTravelCost, LinkCandidate linkCandidateCurrent, LinkCandidate linkCandidateNext) {
		return CoordUtils.calcEuclideanDistance(linkCandidateCurrent.getToNodeCoord(), linkCandidateNext.getFromNodeCoord());
	}

	@Override
	public double getLinkTravelDisutility(Link link, double v, Person person, Vehicle vehicle) {
		return Double.MAX_VALUE;
	}

	@Override
	public double getLinkMinimumTravelDisutility(Link link) {
		return Double.MAX_VALUE;
	}

	@Override
	public double getLinkTravelTime(Link link, double v, Person person, Vehicle vehicle) {
		return 0;
	}
}
