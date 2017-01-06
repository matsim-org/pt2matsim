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

import org.matsim.pt2matsim.gtfs.GtfsDefinitions.RouteTypes;

import java.util.HashMap;
import java.util.Map;


public class RouteImpl implements Route {
	                           
	private String routeId;
	private String shortName;
	private RouteTypes routeType;
	private Map<String, Trip> trips;

	public RouteImpl(String routeId, String shortName, RouteTypes routeType) {
		this.routeId = routeId;
		this.shortName = shortName;
		this.routeType = routeType;
		this.trips = new HashMap<>();
	}

	/**
	 * Puts a new trip
	 * @param trip trip
	 */
	@Override
	public void addTrip(Trip trip) {
		trips.put(trip.getId(), trip);
	}

	@Override
	public Map<String, Trip> getTrips() {
		return trips;
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

}
