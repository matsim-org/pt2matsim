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

import java.util.Map;

/**
 * @author polettif
 */
public interface Route {

	/** required **/
	String getId();

	/** required **/
	String getShortName();

	/** required **/
	String getLongName();

	/** required **/
	GtfsDefinitions.RouteType getRouteType();

	Map<String, Trip> getTrips();

	/**
	 * The base route types are part of the extended set, does not return <tt>null</tt>
	 */
	GtfsDefinitions.ExtendedRouteType getExtendedRouteType();
}
