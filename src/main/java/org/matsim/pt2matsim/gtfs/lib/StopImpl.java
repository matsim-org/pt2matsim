/* *********************************************************************** *
 * project: org.matsim.*
 * Stop.java
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

import org.matsim.api.core.v01.Coord;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class StopImpl implements Stop {

	private final String id;
	private final String name;
	private final int hash;
	private final Collection<Trip> trips = new HashSet<>();

	// optional
	private GtfsDefinitions.LocationType locationType = null;
	private String parentStationId = null;
	private double lon; // West-East
	private double lat; // Sout-North
	private Coord coord;

	public StopImpl(String id, String name, double lon, double lat) {
		this.id = id;
		this.lon = lon;
		this.lat = lat;
		this.coord = new Coord(lon, lat);
		this.name = name;

		int result;
		long temp;
		result = getId().hashCode();
		result = 31 * result + getName().hashCode();
		temp = Double.doubleToLongBits(getLon());
		result = 31 * result + (int) (temp ^ (temp >>> 32));
		temp = Double.doubleToLongBits(getLat());
		result = 31 * result + (int) (temp ^ (temp >>> 32));
		this.hash = result;
	}


	/**
	 * required attribute
	 */
	@Override
	public String getId() {
		return id;
	}

	@Override
	public double getLon() {
		return lon;
	}

	@Override
	public double getLat() {
		return lat;
	}

	/**
	 * required attribute
	 */
	@Override
	public String getName() {
		return name;
	}

	@Override
	public Collection<Trip> getTrips() {
		return Collections.unmodifiableCollection(trips);
	}

	@Override
	public GtfsDefinitions.LocationType getLocationType() {
		return locationType;
	}

	public void setLocationType(GtfsDefinitions.LocationType type) {
		this.locationType = type;
	}

	/**
	 * optional
	 */
	public String getParentStationId() {
		return parentStationId;
	}

	@Override
	public Coord getCoord() {
		return coord;
	}

	public void setCoord(Coord coord) {
		this.coord = coord;
	}

	public void setParentStation(String id) {
		this.parentStationId = id;
	}

	public void addTrip(Trip trip) {
		trips.add(trip);
	}

	@Override
	public boolean equals(Object o) {
		if(this == o) return true;
		if(o == null || getClass() != o.getClass()) return false;

		StopImpl stop = (StopImpl) o;
		return Double.compare(stop.getLon(), getLon()) == 0 && Double.compare(stop.getLat(), getLat()) == 0 && getId().equals(stop.getId()) && getName().equals(stop.getName());
	}

	@Override
	public int hashCode() {
		return hash;
	}

	@Override
	public String toString() {
		return "[stop:" + id + ", \"" + name + "\"]";
	}
}
