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

package org.matsim.pt2matsim.plausibility.log;

import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitRouteStop;
import org.matsim.pt2matsim.plausibility.PlausibilityCheck;
import org.matsim.pt2matsim.tools.ScheduleTools;

/**
 * Plausibility warning if the travel time given by the schedule
 * cannot be achieved by a transit route
 *
 * @author polettif
 */
public class TravelTimeWarning extends AbstractPlausibilityWarning {

	private final TransitRouteStop fromStop;
	private final TransitRouteStop toStop;
	private final double ttActual;
	private final double ttSchedule;

	public TravelTimeWarning(TransitLine transitLine, TransitRoute transitRoute, TransitRouteStop fromStop, TransitRouteStop toStop, double ttActual, double ttSchedule) {
		super(PlausibilityCheck.TRAVEL_TIME_WARNING, transitLine, transitRoute);
		this.fromStop = fromStop;
		this.toStop = toStop;
		this.ttActual = ttActual;
		this.ttSchedule = ttSchedule;

		fromId = fromStop.getStopFacility().getId().toString();
		toId = toStop.getStopFacility().getId().toString();
		expected = ttSchedule;
		actual = ttActual;
		difference = ttActual - ttSchedule;

		linkIdList = ScheduleTools.getSubRouteLinkIds(transitRoute, fromStop.getStopFacility().getLinkId(), toStop.getStopFacility().getLinkId());
	}

	@Override
	public String toString() {
		return "\tTT INCONSISTENT \tstops: "+fromStop.getStopFacility().getId()+" -> "+toStop.getStopFacility().getId()+"\n" +
			   "\t                \t\tdifference: "+String.format("%.1f", (ttActual-ttSchedule))+"\ttt network: "+String.format("%.1f",ttActual)+"\ttt schedule: "+String.format("%.1f",ttSchedule);
	}
}
