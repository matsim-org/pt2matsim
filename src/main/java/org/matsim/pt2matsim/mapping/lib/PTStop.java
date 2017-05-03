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

package org.matsim.pt2matsim.mapping.lib;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitRouteStop;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;
import org.matsim.pt2matsim.mapping.linkCandidateCreation.LinkCandidate;

import java.util.SortedSet;
import java.util.TreeSet;

/**
 * @author polettif
 */
public class PTStop implements PublicTransitStop {

	private final Id<PublicTransitStop> id;
	private final TransitLine transitLine;
	private final TransitRoute transitRoute;
	private final TransitRouteStop transitRouteStop;
	private final SortedSet<LinkCandidate> linkCandidates = new TreeSet<>();

	public PTStop(TransitLine transitLine, TransitRoute transitRoute, TransitRouteStop transitRouteStop) {
		this.id = PublicTransitStop.createId(transitLine, transitRoute, transitRouteStop);
		this.transitLine = transitLine;
		this.transitRoute = transitRoute;
		this.transitRouteStop = transitRouteStop;
	}

	@Override
	public TransitLine getTransitLine() {
		return transitLine;
	}

	@Override
	public TransitRoute getTransitRoute() {
		return transitRoute;
	}

	@Override
	public TransitRouteStop getTransitRouteStop() {
		return transitRouteStop;
	}

	@Override
	public TransitStopFacility getStopFacility() {
		return transitRouteStop.getStopFacility();
	}

	@Override
	public SortedSet<LinkCandidate> getLinkCandidates() {
		return linkCandidates;
	}

	@Override
	public void addLinkCandidate(Link key) {

	}

	@Override
	public void addLinkCandidate(LinkCandidate linkCandidate) {
		this.linkCandidates.add(linkCandidate);
	}

	@Override
	public Id<PublicTransitStop> getId() {
		return id;
	}

	@Override
	public String toString() {
		return "[line:" + transitLine.getId() +
				"][route:" + transitRoute.getId() +
				"[stop:" + transitRouteStop.getStopFacility().getId() +
				" arr:" + transitRouteStop.getArrivalOffset() +
				" dep:" + transitRouteStop.getDepartureOffset() + " ]";
	}
}
