/* *********************************************************************** *
 * project: org.matsim.*
 * Trip.java
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

import org.matsim.pt2matsim.tools.lib.RouteShape;

import java.util.Collection;
import java.util.HashSet;
import java.util.NavigableSet;
import java.util.TreeSet;

/**
 * Container for a GTFS Trip
 */
public class TripImpl implements Trip {

	private final String id;
	private final Service service;
	private final RouteShape shape;
	private final NavigableSet<StopTime> stopTimes;
	private final Collection<Frequency> frequencies;
	private final Route route;

	public TripImpl(String id, Route route, Service service, RouteShape shape) {
		this.route = route;
		this.id = id;
		this.service = service;
		this.shape = shape;
		this.stopTimes = new TreeSet<>();
		this.frequencies = new HashSet<>();
	}

	public TripImpl(String id, Route route, Service service) {
		this.route = route;
		this.id = id;
		this.service = service;
		this.shape = null;
		this.stopTimes = new TreeSet<>();
		this.frequencies = new HashSet<>();
	}

	public void addStopTime(StopTime stopTime) {
		stopTimes.add(stopTime);
	}

	public void addFrequency(Frequency frequency) {
		frequencies.add(frequency);
	}

	/** required */
	@Override
	public String getId() {
		return id;
	}

	/** required */
	@Override
	public Service getService() {
		return service;
	}

	/** required */
	@Override
	public RouteShape getShape() {
		return shape;
	}

	/** required */
	@Override
	public NavigableSet<StopTime> getStopTimes() {
		return stopTimes;
	}

	/** required */
	@Override
	public Collection<Frequency> getFrequencies() {
		return frequencies;
	}

	@Override
	public Route getRoute() {
		return route;
	}

	@Override
	public boolean equals(Object o) {
		if(this == o) return true;
		if(o == null || getClass() != o.getClass()) return false;

		TripImpl trip = (TripImpl) o;
		return getId().equals(trip.getId());
	}

	@Override
	public int hashCode() {
		return getId().hashCode();
	}

	@Override
	public String toString() {
		return "[trip:" + id + "]";
	}
}
