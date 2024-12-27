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

public class StopImpl implements Stop {

	private final String id;
	private final String name;
	private String parentStationId;
	private final GtfsDefinitions.LocationType locationType;
	
	private double lon;
	private double lat;
	private Coord coord;
	private final Collection<Trip> trips = new HashSet<>();

	public StopImpl(String id, String name, GtfsDefinitions.LocationType locationType, String parentStationId) {
		this.id = id;
		this.name = name;
		this.locationType = locationType;
		this.parentStationId = parentStationId;
	}
	
	public StopImpl(String id, String name, double lon, double lat, GtfsDefinitions.LocationType locationType, String parentStationId) {
		this(id, name, locationType, parentStationId);
		setLocation(lon, lat);
	}

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

	@Override
	public String getParentStationId() {
		return parentStationId;
	}

	@Override
	public Coord getCoord() {
		return coord;
	}

	public void setLocation(double lon, double lat) {
		this.lon = lon;
		this.lat = lat;
		this.coord = new Coord(lon, lat);
	}

	public void addTrip(Trip trip) {
		trips.add(trip);
	}

	/**
	 * Coords are ignored, stops are equal even if they've been transformed
	 */
	@Override
	public boolean equals(Object o) {
		if(this == o) return true;
		if(o == null || getClass() != o.getClass()) return false;

		StopImpl stop = (StopImpl) o;

		if(Double.compare(stop.lon, lon) != 0) return false;
		if(Double.compare(stop.lat, lat) != 0) return false;
		if(!id.equals(stop.id)) return false;
		if(!name.equals(stop.name)) return false;
		if(locationType != stop.locationType) return false;
		return parentStationId != null ? parentStationId.equals(stop.parentStationId) : stop.parentStationId == null;
	}

	@Override
	public int hashCode() {
		int result;
		long temp;
		result = id.hashCode();
		result = 31 * result + name.hashCode();
		temp = Double.doubleToLongBits(lon);
		result = 31 * result + (int) (temp ^ (temp >>> 32));
		temp = Double.doubleToLongBits(lat);
		result = 31 * result + (int) (temp ^ (temp >>> 32));
		return result;
	}

	@Override
	public String toString() {
		return "[stop:" + id + " \"" + name + "\" (" + lon + ", " + lat + ")]";
	}

	public void setCoord(Coord newCoord) {
		this.coord = newCoord;
	}
}
