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

package org.matsim.pt2matsim.gtfs;

import org.matsim.api.core.v01.Id;
import org.matsim.pt2matsim.gtfs.lib.*;
import org.matsim.pt2matsim.tools.lib.RouteShape;

import java.util.Collection;
import java.util.Map;

/**
 * An interface to store a GTFS feed to convert afterwards.
 * Note: Not all elements of a feed are represented.
 *
 * @author polettif
 */
public interface GtfsFeed {

	Map<String, Stop> getStops();

	Map<String, Route> getRoutes();

	Map<String, Service> getServices();

	Map<String, Trip> getTrips();

	Map<Id<RouteShape>, RouteShape> getShapes();

	Collection<Transfer> getTransfers();

	/**
	 * transforms all stops to the target coordinate system
	 *
	 * @return the extent of the new stops as array (minE, minN, maxE, maxN)
	 */
	double[] transform(String targetCoordinateSystem);

	String getCurrentCoordSystem();
}
