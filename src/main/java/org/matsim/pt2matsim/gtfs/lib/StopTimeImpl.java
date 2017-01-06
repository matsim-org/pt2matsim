/* *********************************************************************** *
 * project: org.matsim.*
 * StopTime.java
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

import java.util.Date;

/**
 * Container for GTFS StopTime. Contains stopId, arrivalTime and departureTime
 */
public class StopTimeImpl implements StopTime {
	
	private final Integer sequencePosition;
	private final Date arrivalTime;
	private final Date departureTime;
	private String stopId;
	private String tripId;

	public StopTimeImpl(Integer sequencePosition, Date arrivalTime, Date departureTime, String stopId, String tripId) {
		this.sequencePosition = sequencePosition;
		this.arrivalTime = arrivalTime;
		this.departureTime = departureTime;
		this.stopId = stopId;
		this.tripId = tripId;
	}

	/**
	 * required attribute
	 */
	@Override
	public String getStopId() {
		return stopId;
	}

	/**
	 * required attribute
	 */
	@Override
	public String getTripId() {
		return tripId;
	}

	/**
	 * required attribute
	 */
	@Override
	public Date getArrivalTime() {
		return arrivalTime;
	}

	/**
	 * required attribute
	 */
	@Override
	public Date getDepartureTime() {
		return departureTime;
	}

	/**
	 * required attribute
	 * @return the position of the stopTime within the stopSequence
	 */
	@Override
	public Integer getSequencePosition() {
		return sequencePosition;
	}

}
