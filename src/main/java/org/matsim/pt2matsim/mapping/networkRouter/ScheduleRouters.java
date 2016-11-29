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

import org.matsim.api.core.v01.network.Node;
import org.matsim.core.router.util.LeastCostPathCalculator;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt2matsim.mapping.linkCandidateCreation.LinkCandidate;

/**
 * @author polettif
 */
public interface ScheduleRouters {

	/**
	 * Calculate the least cost path between two link candidates depending on the transit line and route
	 */
	LeastCostPathCalculator.Path calcLeastCostPath(LinkCandidate fromLinkCandidate, LinkCandidate toLinkCandidate, TransitLine transitLine, TransitRoute transitRoute);

	LeastCostPathCalculator.Path calcLeastCostPath(Node toNode, Node fromNode, TransitLine transitLine, TransitRoute transitRoute);

	Router getRouter(TransitLine transitLine, TransitRoute transitRoute);

	/**
	 * @deprecated routers should be called via transit line and route
	 */
	@Deprecated
	Router getRouter(String scheduleMode);
}
