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

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;

import java.util.LinkedList;
import java.util.List;

/**
 * @author polettif
 */
public class PseudoTransitRouteImpl implements PseudoTransitRoute {

 	private final Id<TransitLine> transitLineId;
	private final List<PseudoRouteStop> pseudoRouteStops;
	private final TransitRoute transitRoute;
	private final LinkedList<Id<Link>> networkLinkList;

	public PseudoTransitRouteImpl(TransitLine transitLine, TransitRoute transitRoute, List<PseudoRouteStop> pseudoRouteStops, List<Id<Link>> networkLinkList) {
		this.transitLineId = transitLine.getId();
		this.transitRoute = transitRoute;
		this.pseudoRouteStops = pseudoRouteStops;
		this.networkLinkList = new LinkedList<>(networkLinkList);
	}

	@Override
	public Id<TransitLine> getTransitLineId() {
		return transitLineId;
	}

	@Override
	public TransitRoute getTransitRoute() {
		return transitRoute;
	}

	@Override
	public List<PseudoRouteStop> getPseudoStops() {
		return pseudoRouteStops;
	}

	@Override
	public List<Id<Link>> getNetworkLinkIdList() {
		return networkLinkList;
	}
}
