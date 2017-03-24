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

	// todo implement optional parent stops

	private final String id;
	private final Coord coord;
	private final String name;
	private final int hash;
	private final Set<Trip> trips = new HashSet<>();
	private String parentStationId = null;

	public StopImpl(String id, String name, Coord coord) {
		this.id = id;
		this.coord = coord;
		this.name = name;
		this.hash = (id + name + coord.toString()).hashCode();
	}


	/**
	 * required attribute
	 */
	@Override
	public String getId() {
		return id;
	}

	/**
	 * required attribute
	 */
	@Override
	public Coord getCoord() {
		return coord;
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

	/**
	 * optional
	 */
	public String getParentStationId() {
		return parentStationId;
	}

	public void setParentStation(String id) {
		this.parentStationId = id;
	}

	public void addTrip(Trip trip) {
		trips.add(trip);
	}

	@Override
	public boolean equals(Object obj) {
		if(this == obj)
			return true;
		if(obj == null)
			return false;
		if(getClass() != obj.getClass())
			return false;

		Stop other = (Stop) obj;
		return (other.getId().equals(id) &&
				other.getCoord().equals(coord) &&
				other.getName().equals(name));
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
