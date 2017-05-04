/* *********************************************************************** *
 * project: org.matsim.*
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2014 by the members listed in the COPYING,        *
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
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.router.util.LeastCostPathCalculator;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitRouteStop;
import org.matsim.pt2matsim.mapping.MapperModule;
import org.matsim.pt2matsim.mapping.linkCandidateCreation.LinkCandidate;

/**
 * All Routers (i.e. LeastCostPathCalculators) for the transit routes are stored within
 * an implementation of this interface. That way, the implementation can use different
 * routers for different transit routes while keeping the access the same.
 *
 * @author polettif
 */
public interface ScheduleRouters extends MapperModule {

	/**
	 * Calculate the least cost path between two link candidates depending on the transit line and route
	 */
	LeastCostPathCalculator.Path calcLeastCostPath(LinkCandidate fromLinkCandidate, LinkCandidate toLinkCandidate, TransitLine transitLine, TransitRoute transitRoute);

	LeastCostPathCalculator.Path calcLeastCostPath(Id<Node> fromNode, Id<Node> toNode, TransitLine transitLine, TransitRoute transitRoute);

	double getMinimalTravelCost(TransitRouteStop fromTransitRouteStop, TransitRouteStop toTransitRouteStop, TransitLine transitLine, TransitRoute transitRoute);

	double getLinkCandidateTravelCost(LinkCandidate linkCandidateCurrent);

	/**
	 * Wrapper class to enable synchronized access to least cost path calculators
	 */
	class PathCalculator {

		private final LeastCostPathCalculator leastCostPathCalculator;

		PathCalculator(LeastCostPathCalculator leastCostPathCalculator) {
			this.leastCostPathCalculator = leastCostPathCalculator;
		}

		synchronized LeastCostPathCalculator.Path calcPath(Node fromNode, Node toNode) {
			return leastCostPathCalculator.calcLeastCostPath(fromNode, toNode, 0, null, null);
		}

	}
}
