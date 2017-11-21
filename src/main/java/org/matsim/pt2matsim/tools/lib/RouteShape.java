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

package org.matsim.pt2matsim.tools.lib;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Identifiable;

import java.util.List;
import java.util.SortedMap;

/**
 * A polyline representing a route as a list of point coordinates.
 *
 * Custom class, should consider switching to a geotools implementation
 *
 * @author polettif
 */
public interface RouteShape extends Identifiable {

	Id<RouteShape> getId();

	void addPoint(Coord point, int pos);

	SortedMap<Integer, Coord> getCoordsSorted();

	List<Coord> getCoords();

	Coord[] getExtent();
}
