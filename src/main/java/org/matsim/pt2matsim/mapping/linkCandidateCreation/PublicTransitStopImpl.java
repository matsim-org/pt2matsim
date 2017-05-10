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

package org.matsim.pt2matsim.mapping.linkCandidateCreation;

import org.matsim.api.core.v01.Id;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitRouteStop;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

/**
 * @author polettif
 */
public class PublicTransitStopImpl implements PublicTransitStop {

	private final Id<PublicTransitStop> id;
	private final TransitLine transitLine;
	private final TransitRoute transitRoute;
	private final TransitRouteStop transitRouteStop;

	public PublicTransitStopImpl(TransitLine transitLine, TransitRoute transitRoute, TransitRouteStop transitRouteStop) {
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
	public Id<PublicTransitStop> getId() {
		return id;
	}

	@Override
	public String toString() {
		return id.toString();
	}

	@Override
	public boolean equals(Object o) {
		if(this == o) return true;
		if(o == null || getClass() != o.getClass()) return false;

		PublicTransitStopImpl that = (PublicTransitStopImpl) o;

		if(transitLine != null ? !transitLine.equals(that.transitLine) : that.transitLine != null) return false;
		if(transitRoute != null ? !transitRoute.equals(that.transitRoute) : that.transitRoute != null) return false;
		return transitRouteStop != null ? transitRouteStop.equals(that.transitRouteStop) : that.transitRouteStop == null;
	}

	@Override
	public int hashCode() {
		int result = transitLine != null ? transitLine.hashCode() : 0;
		result = 31 * result + (transitRoute != null ? transitRoute.hashCode() : 0);
		result = 31 * result + (transitRouteStop != null ? transitRouteStop.hashCode() : 0);
		return result;
	}
}
