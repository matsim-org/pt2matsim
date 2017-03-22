/* *********************************************************************** *
 * project: org.matsim.*
 * Route.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2011 by the members listed in the COPYING,        *
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

package org.matsim.pt2matsim.gtfs.lib;

import org.matsim.pt2matsim.gtfs.lib.GtfsDefinitions.RouteTypes;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;


public class RouteImpl implements Route {

	private final String routeId;
	private final String shortName;
	private final RouteTypes routeType;
	private final Map<String, Trip> trips = new HashMap<>();

	public RouteImpl(String routeId, String shortName, RouteTypes routeType) {
		this.routeId = routeId;
		this.shortName = shortName;
		this.routeType = routeType;
	}

	/**
	 * adds a new trip
	 * @param trip trip
	 */
	public void addTrip(Trip trip) {
		trips.put(trip.getId(), trip);
	}

	@Override
	public Map<String, Trip> getTrips() {
		return Collections.unmodifiableMap(trips);
	}

	/**
	 * required attribute
 	 */
	@Override
	public String getId() {
		return routeId;
	}

	/**
	 * required attribute
	 */
	@Override
	public String getShortName() {
		return shortName;
	}

	/**
	 * required attribute
	 */
	@Override
	public RouteTypes getRouteType() {
		return routeType;
	}

	@Override
	public boolean equals(Object obj) {
		if(this == obj)
			return true;
		if(obj == null)
			return false;
		if(getClass() != obj.getClass())
			return false;

		Route other = (Route) obj;
		return (other.getId().equals(routeId) &&
				other.getRouteType().equals(routeType) &&
				other.getTrips().keySet().equals(trips.keySet()));

	}

	@Override
	public int hashCode() {
		return (routeId + routeType.toString() + trips.keySet().toString()).hashCode();
	}
}
