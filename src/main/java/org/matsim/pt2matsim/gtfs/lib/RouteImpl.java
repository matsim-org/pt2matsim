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

import org.matsim.pt2matsim.gtfs.lib.GtfsDefinitions.RouteType;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class RouteImpl implements Route {

	private final String routeId;
	private final String shortName;
	private final String longName;
	private final RouteType routeType;
	private final GtfsDefinitions.ExtendedRouteType extendedRouteType;

	private final Map<String, Trip> trips = new HashMap<>();

	public RouteImpl(String routeId, String shortName, String longName, RouteType routeType) {
		this.routeId = routeId;
		this.shortName = shortName;
		this.longName = longName;
		this.routeType = routeType;
		this.extendedRouteType = GtfsDefinitions.ExtendedRouteType.getExtendedRouteType(routeType);
	}

	public RouteImpl(String routeId, String shortName, String longName, GtfsDefinitions.ExtendedRouteType extendedRouteType) {
		this.routeId = routeId;
		this.shortName = shortName;
		this.longName = longName;
		this.routeType = extendedRouteType.routeType;
		this.extendedRouteType = extendedRouteType;
	}

	/**
	 * adds a new trip
	 * @param trip trip
	 */
	public void addTrip(Trip trip) {
		trips.put(trip.getId(), trip);
	}

	/** required */
	@Override
	public String getId() {
		return routeId;
	}

	/** required */
	@Override
	public String getShortName() {
		return shortName;
	}

	/** required **/
	@Override
	public String getLongName() {
		return longName;
	}

	/** required */
	@Override
	public RouteType getRouteType() {
		return routeType;
	}

	@Override
	public Map<String, Trip> getTrips() {
		return Collections.unmodifiableMap(trips);
	}

	@Override
	public GtfsDefinitions.ExtendedRouteType getExtendedRouteType() {
		return extendedRouteType;
	}

	@Override
	public boolean equals(Object o) {
		if(this == o) return true;
		if(o == null || getClass() != o.getClass()) return false;

		RouteImpl route = (RouteImpl) o;

		if(!routeId.equals(route.routeId)) return false;
		if(!shortName.equals(route.shortName)) return false;
		if(routeType != route.routeType) return false;
		return trips.equals(route.trips);
	}

	@Override
	public int hashCode() {
		int result = routeId.hashCode();
		result = 31 * result + shortName.hashCode();
		result = 31 * result + routeType.hashCode();
		result = 31 * result + trips.hashCode();
		return result;
	}

	@Override
	public String toString() {
		return "[route:" + routeId + ", \"" + shortName + "\"]";
	}
}
