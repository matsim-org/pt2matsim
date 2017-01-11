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
import org.matsim.pt2matsim.gtfs.lib.Route;
import org.matsim.pt2matsim.gtfs.lib.Service;
import org.matsim.pt2matsim.gtfs.lib.Stop;
import org.matsim.pt2matsim.lib.RouteShape;

import java.util.Map;

/**
 * An interface to load and convert GTFS feeds
 *
 * @author polettif
 */
public interface GtfsFeed {

	Map<String, Stop> getStops();

	Map<String, Route> getRoutes();

	Map<Id<RouteShape>, RouteShape> getShapes();

	boolean usesFrequencies();

	Map<String, Service> getServices();

}
