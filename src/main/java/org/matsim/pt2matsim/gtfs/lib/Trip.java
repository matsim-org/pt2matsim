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

import org.matsim.api.core.v01.network.Link;

import java.util.*;

/**
 * Container for a GTFS Trip
 */
public class Trip {
	
	//Attributes
	private final String tripId;
	private final String serviceId;
	private final Service service;
	private final Shape shape;
	private final String name;
	private final SortedMap<Integer,StopTime> stopTimes;
	private final List<Frequency> frequencies;
	private List<Link> links;
	private Date previousArrivalTime;

	//Methods
	public Trip(String tripId, Service service, Shape shape, String name) {
		super();
		this.tripId = tripId;
		this.service = service;
		this.serviceId = service.getId();
		this.shape = shape;
		this.name = name;
		stopTimes = new TreeMap<>();
		frequencies = new ArrayList<>();
		links = new ArrayList<>();
	}

	/**
	 * @return the service
	 */
	public Service getService() {
		return service;
	}

	/**
	 * @return the shape
	 */
	public Shape getShape() {
		return shape;
	}

	/**
	 * @return the name
	 */
	public String getName() {
		return name;
	}

	/**
	 * @return the stopTimes
	 */
	public SortedMap<Integer, StopTime> getStopTimes() {
		return stopTimes;
	}

	/**
	 * @return the frequencies
	 */
	public List<Frequency> getFrequencies() {
		return frequencies;
	}

	/**
	 * Puts a new stopTime
	 * @param stopSequencePosition which stop number in the stopSequence this stopTime is referencing
	 */
	public void putStopTime(Integer stopSequencePosition, StopTime stopTime) {
		stopTimes.put(stopSequencePosition, stopTime);
	}

	/**
	 * Puts a new stop without a time, uses the time of the preceding stop
	 * @param stopSequencePosition which stop number in the stopSequence this stopTime is referencing
	 */
	public void putStop(Integer stopSequencePosition) {
		stopTimes.put(stopSequencePosition, stopTimes.get(stopSequencePosition - 1));
	}

	/**
	 * Adds a new frequency
	 */
	public void addFrequency(Frequency frequency) {
		frequencies.add(frequency);
	}

	/**
	 * @return the tripId
	 */
	public String getId() {
		return tripId;
	}

	public String getServiceId() {
		return serviceId;
	}


}
