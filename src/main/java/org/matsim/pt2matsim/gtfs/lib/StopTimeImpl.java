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

/**
 * Container for GTFS StopTime. Contains stopId, arrivalTime and departureTime
 */
public class StopTimeImpl implements StopTime {

	private final Integer sequencePosition;
	private final int arrivalTime;
	private final int departureTime;
	private final Stop stop;
	private final Trip trip;
	private final int hash;

	public StopTimeImpl(Integer sequencePosition, int arrivalTime, int departureTime, Stop stop, Trip trip) {
		this.sequencePosition = sequencePosition;
		this.arrivalTime = arrivalTime;
		this.departureTime = departureTime;
		this.stop = stop;
		this.trip = trip;
		this.hash = (sequencePosition.toString() + stop.getId() + trip.getId()).hashCode() + arrivalTime + departureTime;
	}


	@Override
	public Stop getStop() {
		return stop;
	}

	@Override
	public Trip getTrip() {
		return trip;
	}

	/**
	 * required attribute
	 */
	@Override
	public int getArrivalTime() {
		return arrivalTime;
	}

	/**
	 * required attribute
	 */
	@Override
	public int getDepartureTime() {
		return departureTime;
	}

	/**
	 * required attribute
	 *
	 * @return the position of the stopTime within the stopSequence
	 */
	@Override
	public Integer getSequencePosition() {
		return sequencePosition;
	}

	@Override
	public boolean equals(Object obj) {
		if(this == obj)
			return true;
		if(obj == null)
			return false;
		if(getClass() != obj.getClass())
			return false;

		StopTime other = (StopTime) obj;
		return (other.getStop().getId().equals(stop.getId()) &&
				other.getSequencePosition().equals(sequencePosition) &&
				other.getArrivalTime() == arrivalTime &&
				other.getDepartureTime() == departureTime);

	}

	@Override
	public int hashCode() {
		return hash;
	}

}
