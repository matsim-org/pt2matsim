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

import org.matsim.pt2matsim.lib.RouteShape;

import java.util.List;
import java.util.SortedMap;

/**
 * @author polettif
 */
public interface Trip {

	void addFrequency(Frequency frequency);

	boolean hasShape();

	String getId();

	Service getService();

	RouteShape getShape();

	String getName();

	SortedMap<Integer, StopTime> getStopTimes();

	List<Frequency> getFrequencies();

	void addStopTime(StopTime newStopTime);
}
