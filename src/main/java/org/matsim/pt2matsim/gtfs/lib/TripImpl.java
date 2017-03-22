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

import org.matsim.pt2matsim.lib.RouteShape;

import java.util.ArrayList;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Container for a GTFS Trip
 */
public class TripImpl implements Trip {
	
	private final String tripId;
	private final Service service;
	private final RouteShape shape;
	private final String name;
	private final SortedMap<Integer, StopTime> stopTimes;
	private final List<Frequency> frequencies;

	public TripImpl(String tripId, Service service, RouteShape shape, String name) {
		this.tripId = tripId;
		this.service = service;
		this.shape = shape;
		this.name = name;
		stopTimes = new TreeMap<>();
		frequencies = new ArrayList<>();
	}

	public void addStopTime(StopTime stopTime) {
		stopTimes.put(stopTime.getSequencePosition(), stopTime);
	}

	public void addFrequency(Frequency frequency) {
		frequencies.add(frequency);
	}

	@Override
	public boolean hasShape() {
		return shape != null;
	}

	/**
	 * required attribute
	 */
	@Override
	public String getId() {
		return tripId;
	}

	/**
	 * required attribute
	 */
	@Override
	public Service getService() {
		return service;
	}

	/**
	 * required attribute
	 */
	@Override
	public RouteShape getShape() {
		return shape;
	}

	/**
	 * required attribute
	 */
	@Override
	public String getName() {
		return name;
	}

	/**
	 * required attribute
	 */
	@Override
	public SortedMap<Integer, StopTime> getStopTimes() {
		return stopTimes;
	}

	/**
	 * required attribute
	 */
	@Override
	public List<Frequency> getFrequencies() {
		return frequencies;
	}

	@Override
	public boolean equals(Object obj) {
		if(this == obj)
			return true;
		if(obj == null)
			return false;
		if(getClass() != obj.getClass())
			return false;

		Trip other = (Trip) obj;
		return (other.getId().equals(tripId));
	}

	@Override
	public int hashCode() {
		return tripId.hashCode();
	}
}
