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
import org.matsim.core.utils.collections.Tuple;
import org.matsim.core.utils.geometry.geotools.MGC;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt2matsim.lib.RouteShape;

import java.util.*;

public class GtfsShape implements RouteShape {

	/**
	 * The id
	 */
	private Id<RouteShape> id;

	/**
	 * The points of the shape
	 */
	private SortedMap<Integer, Coord> points = new TreeMap<>();

	/**
	 * A shape can be referenced to multiple transit routes
	 */
	private Set<Tuple<Id<TransitLine>, Id<TransitRoute>>> transitRoutes = new HashSet<>();
	private Coord[] extent = new Coord[]{new Coord(Double.MAX_VALUE, Double.MAX_VALUE), new Coord(Double.MIN_VALUE, Double.MIN_VALUE)};


	/**
	 * Constructs
	 */
	public GtfsShape(String id) {
		this.id = Id.create(id, RouteShape.class);
	}

	public GtfsShape(Id<RouteShape> shapeId) {
		this.id = shapeId;
	}

	/**
	 * @return the id
	 */
	@Override
	public Id<RouteShape> getId() {
		return id;
	}

	/**
	 * @return the points
	 */
	public SortedMap<Integer, Coord> getPoints() {
		return points;
	}

	public List<Coord> getCoords() {
		return new ArrayList<>(points.values());
	}

	@Override
	public Set<Tuple<Id<TransitLine>, Id<TransitRoute>>> getTransitRoutes() {
		return transitRoutes;
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

		if(point.getX() < extent[0].getX()) {
			extent[0].setX(point.getX());
		}
		if(point.getY() < extent[0].getY()) {
			extent[0].setY(point.getY());
		}
		if(point.getX() > extent[1].getX()) {
			extent[1].setX(point.getX());
		}
		if(point.getY() > extent[1].getY()) {
			extent[1].setY(point.getY());
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

	public void addTransitRoute(Id<TransitLine> transitLineId, Id<TransitRoute> transitRouteId) {
		this.transitRoutes.add(new Tuple<>(transitLineId, transitRouteId));
	}

	public Coord[] getExtent() {
		return extent;
	}
}
