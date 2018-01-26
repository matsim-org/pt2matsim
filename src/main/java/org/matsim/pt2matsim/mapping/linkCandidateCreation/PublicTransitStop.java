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
import org.matsim.api.core.v01.Identifiable;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitRouteStop;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

/**
 * Unique transitRouteStop. {@link TransitRouteStop} doesn't
 * contain information on the transit route and line it is a
 * part of. Thus, TransitRouteStops can't be used as identifier
 * for multiple "stops" occurring with different transit routes
 * on the same stop facility.
 *
 * @author polettif
 */
public interface PublicTransitStop extends Identifiable<PublicTransitStop> {

	TransitLine getTransitLine();

	TransitRoute getTransitRoute();

	TransitRouteStop getTransitRouteStop();

	TransitStopFacility getStopFacility();

	static Id<PublicTransitStop> createId(TransitLine transitLine, TransitRoute transitRoute, TransitRouteStop transitRouteStop) {
		return Id.create("[line:" + transitLine.getId() +
				"][route:" + transitRoute.getId() +
				"][stop:" + transitRouteStop.getStopFacility().getId() +
				" arr:" + transitRouteStop.getArrivalOffset() +
				" dep:" + transitRouteStop.getDepartureOffset() + "]",
				PublicTransitStop.class);
	}

}
