/* *********************************************************************** *
 * project: org.matsim.*
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2016 by the members listed in the COPYING,        *
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

/**
 * @author polettif
 */
public interface Stop {

	/**
	 * [required]
	 * ID that uniquely identifies a stop or station. Multiple routes may use the same stop. The stop_id is dataset unique.
	 */
	String getId();

	/** required **/
	double getLon();

	/** required **/
	double getLat();

	/**
	 * [required] Name of a stop or station
	 */
	String getName();

	/**
	 * [optional] Identifies whether this stop represents a stop or station.
	 *
	 * @return <tt>null</tt> when not defined
	 */
	GtfsDefinitions.LocationType getLocationType();

	/**
	 * [optional] For stops that are physically located inside stations, this field identifies the station associated
	 * with the stop. To use this field, stops.txt must also contain a row where this stop ID is assigned
	 * location type=1.
	 *
	 * @return <tt>null</tt> when not defined
	 */
	String getParentStationId();

	Collection<Trip> getTrips();

	Coord getCoord();
}
