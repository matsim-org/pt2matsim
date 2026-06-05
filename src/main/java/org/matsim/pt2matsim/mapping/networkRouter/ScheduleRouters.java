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
import org.matsim.api.core.v01.network.Link;
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

	LeastCostPathCalculator.Path calcLeastCostPath(Id<Link> fromLink, Id<Link> toLink, TransitLine transitLine, TransitRoute transitRoute);

	/**
	 * Bounded variant of {@link #calcLeastCostPath(LinkCandidate, LinkCandidate, TransitLine, TransitRoute)}.
	 * Implementations may abort the search and return {@code null} as soon as it is provable that
	 * no path with cost {@code <= maxCost} exists. {@link Double#POSITIVE_INFINITY} disables the cutoff.
	 *
	 * <p>The default implementation ignores {@code maxCost} and delegates to the unbounded overload,
	 * preserving behaviour for implementations that do not yet support the cutoff.</p>
	 */
	default LeastCostPathCalculator.Path calcLeastCostPath(LinkCandidate fromLinkCandidate, LinkCandidate toLinkCandidate,
			TransitLine transitLine, TransitRoute transitRoute, double maxCost) {
		return calcLeastCostPath(fromLinkCandidate, toLinkCandidate, transitLine, transitRoute);
	}

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

		synchronized LeastCostPathCalculator.Path calcPath(Link fromLink, Link toLink) {
			return leastCostPathCalculator.calcLeastCostPath(fromLink, toLink, 0, null, null);
		}

		/**
		 * Bounded variant of {@link #calcPath(Link, Link)}: requires MATSim's {@code LeastCostPathCalculator}
		 * to provide the {@code maxCost} overload (added 2027.0). Implementations that do not honour the
		 * cutoff will fall back to the unbounded search via the default method on the MATSim interface.
		 */
		synchronized LeastCostPathCalculator.Path calcPath(Link fromLink, Link toLink, double maxCost) {
			return leastCostPathCalculator.calcLeastCostPath(fromLink, toLink, 0, null, null, maxCost);
		}

	}
}
