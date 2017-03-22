/* *********************************************************************** *
 * project: org.matsim.*
 * Shape.java
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

import com.vividsolutions.jts.geom.Coordinate;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.core.utils.geometry.geotools.MGC;
import org.matsim.pt2matsim.lib.RouteShape;

import java.util.ArrayList;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Implementation of RouteShape, represents a GTFS shape (i.e. a sequence
 * of point coordinates.
 *
 * @author polettif
 */
public class GtfsShape implements RouteShape {

	private Id<RouteShape> id;

	private SortedMap<Integer, Coord> points = new TreeMap<>();

	private double extentSWx = Double.MAX_VALUE;
	private double extentSWy = Double.MAX_VALUE;
	private double extentNEx = Double.MIN_VALUE;
	private double extentNEy = Double.MIN_VALUE;

	public GtfsShape(String id) {
		this.id = Id.create(id, RouteShape.class);
	}

	public GtfsShape(Id<RouteShape> shapeId) {
		this.id = shapeId;
	}

	// required attribute
	@Override
	public Id<RouteShape> getId() {
		return id;
	}

	public SortedMap<Integer, Coord> getCoordsSorted() {
		return points;
	}

	public List<Coord> getCoords() {
		return new ArrayList<>(points.values());
	}

	/**
	 * Adds a new point
	 */
	@Override
	public void addPoint(Coord point, int pos) {
		Coord check = points.put(pos, point);

		if(check != null && (check.getX() != point.getX() || check.getY() != point.getY())) {
			throw new IllegalArgumentException("Sequence position " + pos + " already defined in shape " + id);
		}

		if(point.getX() < extentSWx) {
			extentSWx = point.getX();
		}
		if(point.getY() < extentSWy) {
			extentSWy = point.getY();
		}
		if(point.getX() > extentNEx) {
			extentNEx = point.getX();
		}
		if(point.getY() > extentNEy) {
			extentNEy = point.getY();
		}
	}

	public Coordinate[] getCoordinates() {
		if(points.size() == 0) {
			return null;
		} else {
			int i = 0;
			Coordinate[] coordinates = new Coordinate[points.values().size()];
			for(Coord coord : points.values()) {
				coordinates[i++] = MGC.coord2Coordinate(coord);
			}
			return coordinates;
		}
	}

	/**
	 * @return the maximal SW and NE corners of the shape
	 */
	@Override
	public Coord[] getExtent() {
		return new Coord[]{new Coord(extentSWx, extentSWy), new Coord(extentNEx, extentNEy)};
	}

	@Override
	public String toString() {
		return id.toString() + " [" + points.size() + " points]";
	}

	@Override
	public boolean equals(Object obj) {
		if(this == obj)
			return true;
		if(obj == null)
			return false;
		if(getClass() != obj.getClass())
			return false;

		RouteShape other = (RouteShape) obj;
		return (other.getId().equals(id) &&
				other.getCoordsSorted().equals(points));
	}

	@Override
	public int hashCode() {
		return (id + points.values().toString()).hashCode();
	}
}
