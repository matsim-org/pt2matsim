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
import org.matsim.api.core.v01.Id;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;

import java.util.List;
import java.util.Set;
import java.util.SortedMap;

/**
 * @author polettif
 */
public interface Shape {

	String getId();

	void addPoint(Coord point, int pos);

	SortedMap<Integer, Coord> getPoints();

	List<Coord> getCoords();

	Set<Tuple<Id<TransitLine>, Id<TransitRoute>>> getTransitRoutes();

	void addTransitRoute(Id<TransitLine> transitLineId, Id<TransitRoute> transitRouteId);
}
