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

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.pt2matsim.tools.lib.RouteShape;

import java.util.*;

/**
 * Implementation of RouteShape, represents a GTFS shape (i.e. a sequence
 * of point coordinates.
 *
 * @author polettif
 */
public class GtfsShape implements RouteShape {

	private Id<RouteShape> id;

	private SortedMap<Integer, Coord> coordSorted = new TreeMap<>();

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

	/** required attribute */
	@Override
	public Id<RouteShape> getId() {
		return id;
	}

	public SortedMap<Integer, Coord> getCoordsSorted() {
		return coordSorted;
	}

	public List<Coord> getCoords() {
		return new ArrayList<>(coordSorted.values());
	}

	public void transformCoords(CoordinateTransformation transformation) {
		for(Map.Entry<Integer, Coord> entry : this.coordSorted.entrySet()) {
			Coord transformedCoord = transformation.transform(entry.getValue());
			this.coordSorted.put(entry.getKey(), transformedCoord);
		}
	}

	/**
	 * Adds a new point
	 */
	@Override
	public void addPoint(Coord point, int pos) {
		Coord check = coordSorted.put(pos, point);

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

	/**
	 * @return the maximal SW and NE corners of the shape
	 */
	@Override
	public Coord[] getExtent() {
		return new Coord[]{new Coord(extentSWx, extentSWy), new Coord(extentNEx, extentNEy)};
	}

	@Override
	public boolean equals(Object o) {
		if(this == o) return true;
		if(o == null || getClass() != o.getClass()) return false;

		GtfsShape gtfsShape = (GtfsShape) o;

		if(!id.equals(gtfsShape.id)) return false;
		return coordSorted.equals(gtfsShape.coordSorted);
	}

	@Override
	public int hashCode() {
		int result = id.hashCode();
		result = 31 * result + coordSorted.hashCode();
		return result;
	}

	@Override
	public String toString() {
		return id.toString() + " [" + coordSorted.size() + " coordSorted]";
	}
}
