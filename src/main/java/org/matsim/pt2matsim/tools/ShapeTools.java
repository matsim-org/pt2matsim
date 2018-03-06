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

package org.matsim.pt2matsim.tools;

import com.opencsv.CSVReader;
import com.vividsolutions.jts.geom.Coordinate;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.geometry.geotools.MGC;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;
import org.matsim.core.utils.gis.PolylineFeatureFactory;
import org.matsim.core.utils.gis.ShapeFileWriter;
import org.matsim.pt2matsim.gtfs.GtfsFeed;
import org.matsim.pt2matsim.gtfs.GtfsFeedImpl;
import org.matsim.pt2matsim.gtfs.lib.GtfsDefinitions;
import org.matsim.pt2matsim.gtfs.lib.GtfsShape;
import org.matsim.pt2matsim.gtfs.lib.Route;
import org.matsim.pt2matsim.gtfs.lib.Trip;
import org.matsim.pt2matsim.tools.lib.RouteShape;
import org.opengis.feature.simple.SimpleFeature;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

import static org.matsim.pt2matsim.tools.CsvTools.getIndices;

/**
 * Provides tools to calculate RouteShape attributes such as minimal distances and lengths.
 *
 * @author polettif
 */
public final class ShapeTools {

	/**
	 * Calculates the minimal distance from a point to a given routeShape
	 */
	public static double calcMinDistanceToShape(Coord point, RouteShape shape) {
		List<Coord> shapePoints = new ArrayList<>(shape.getCoordsSorted().values());
		double minDist = Double.MAX_VALUE;
		// look for the minimal distance between the current point and all pairs of shape points
		for(int i = 0; i < shapePoints.size() - 1; i++) {
			double dist = CoordUtils.distancePointLinesegment(shapePoints.get(i), shapePoints.get(i + 1), point);
			if(dist < minDist) {
				minDist = dist;
			}
		}
		return minDist;
	}

	/**
	 * Calculates the minimal distance from a link to a given routeShape as
	 * average distance of the the two link nodes and the center of the link.
	 */
	public static double calcMinDistanceToShape(Link link, RouteShape shape) {
		Coord c1 = link.getFromNode().getCoord();
		Coord c2 = link.getToNode().getCoord();
		Coord c3 = CoordUtils.getCenter(c1, c2);

		double dist1 = ShapeTools.calcMinDistanceToShape(c1, shape);
		double dist2 = ShapeTools.calcMinDistanceToShape(c2, shape);
		double dist3 = ShapeTools.calcMinDistanceToShape(c3, shape);

		return (dist1 + dist2 + dist3) / 3.0;
	}

	/**
	 * Calculates the minimal distance from a link to a given routeShape.
	 * Distances are calculated in intervals of 5 map units.
	 *
	 * @deprecated if one of the end nodes is on the shape, distance is 0 even if
	 * the link is perpendicular.
	 */
	@Deprecated
	public static double calcMinDistanceToShapeInterval(Link link, RouteShape shape) {
		double measureInterval = 5;

		double minDist = Double.MAX_VALUE;
		double lengthOnLink = 0;
		double azimuth = CoordTools.getAzimuth(link.getFromNode().getCoord(), link.getToNode().getCoord());
		double linkLength = CoordUtils.calcEuclideanDistance(link.getFromNode().getCoord(), link.getToNode().getCoord());

		while(lengthOnLink < linkLength) {
			Coord currentPoint = CoordTools.calcNewPoint(link.getFromNode().getCoord(), azimuth, lengthOnLink);

			// look for shortest distance to shape
			double dist = ShapeTools.calcMinDistanceToShape(currentPoint, shape);
			if(dist < minDist) {
				minDist = dist;
			}
			lengthOnLink += measureInterval;
		}
		return minDist;
	}

	/**
	 * @return all nodes within a buffer distance from the shape
	 */
	public static Collection<Node> getNodesWithinBuffer(Network network, RouteShape shape, double buffer) {
		Set<Node> nodesWithinBuffer = new HashSet<>();

		List<Coord> coords = shape.getCoords();

		for(int i = 0; i < coords.size() - 1; i++) {
			Coord current = coords.get(i);
			Coord next = coords.get(i + 1);

			double dist = 0;
			double delta = buffer / 2;
			double maxDist = CoordUtils.calcEuclideanDistance(current, next);
			double az = CoordTools.getAzimuth(current, next);
			do {
				nodesWithinBuffer.addAll(NetworkUtils.getNearestNodes(network, CoordTools.calcNewPoint(current, az, dist), buffer));
				dist += delta;
			} while(dist < maxDist);
		}
		nodesWithinBuffer.addAll(NetworkUtils.getNearestNodes(network, coords.get(coords.size() - 1), buffer));

		return nodesWithinBuffer;
	}


	/**
	 * @return the length of a shape (sum of all its segment lengths)
	 */
	public static double getShapeLength(RouteShape shape) {
		double length = 0;
		List<Coord> coords = shape.getCoords();
		for(int i = 0; i < coords.size()-1; i++) {
			length += CoordUtils.calcEuclideanDistance(coords.get(i), coords.get(i + 1));
		}
		return length;
	}

	/**
	 * Converts a list of link ids to an array of coordinates for shp features
	 */
	public static Coordinate[] linkIdList2Coordinates(Network network, List<Id<Link>> linkIdList) {
		List<Coordinate> coordList = new ArrayList<>();
		for(Id<Link> linkId : linkIdList) {
			if(network.getLinks().containsKey(linkId)) {
				coordList.add(MGC.coord2Coordinate(network.getLinks().get(linkId).getFromNode().getCoord()));
			} else {
				throw new IllegalArgumentException("Link " + linkId + " not found in network");
			}
		}
		coordList.add(MGC.coord2Coordinate(network.getLinks().get(linkIdList.get(linkIdList.size() - 1)).getToNode().getCoord()));
		Coordinate[] coordinates = new Coordinate[coordList.size()];
		return coordList.toArray(coordinates);
	}

	/**
	 * Allows converting GTFS data to an ESRI shapefiles.
	 */
	@Deprecated
	public static void writeGtfsTripsToFile(GtfsFeed gtfsFeed, Set<String> serviceIds, String outputCoordinateSystem, String outFile) {
		Collection<SimpleFeature> features = new ArrayList<>();

		PolylineFeatureFactory ff = new PolylineFeatureFactory.Builder()
				.setName("gtfs_shapes")
				.setCrs(MGC.getCRS(outputCoordinateSystem))
				.addAttribute("shape_id", String.class)
				.addAttribute("trip_id", String.class)
				.addAttribute("route_id", String.class)
				.addAttribute("route_name", String.class)
				.create();


		GtfsFeedImpl gtfs = (GtfsFeedImpl) gtfsFeed;

		for(Route gtfsRoute : gtfs.getRoutes().values()) {
			for(Trip trip : gtfsRoute.getTrips().values()) {
				boolean useTrip = false;
				if(serviceIds != null) {
					for(String serviceId : serviceIds) {
						if(trip.getService().getId().equals(serviceId)) {
							useTrip = true;
							break;
						}
					}
				} else {
					useTrip = true;
				}

				if(useTrip) {
					RouteShape shape = trip.getShape();
					if(shape != null) {

						Collection<Coord> points = shape.getCoordsSorted().values();
						int i = 0;
						Coordinate[] coordinates = new Coordinate[points.size()];
						for(Coord coord : points) {
							coordinates[i++] = MGC.coord2Coordinate(coord);
						}

						SimpleFeature f = ff.createPolyline(coordinates);
						f.setAttribute("shape_id", shape.getId());
						f.setAttribute("trip_id", trip.getId());
						f.setAttribute("route_id", gtfsRoute.getId());
						f.setAttribute("route_name", gtfsRoute.getShortName());
						features.add(f);
					}
				}
			}
		}
		ShapeFileWriter.writeGeometries(features, outFile);
	}

	/**
	 * Writes the given shapes to a shape file
	 */
	public static void writeESRIShapeFile(Collection<? extends RouteShape> shapes, String outputCoordinateSystem, String filename) {
		Collection<SimpleFeature> features = new ArrayList<>();

		PolylineFeatureFactory ff = new PolylineFeatureFactory.Builder()
				.setName("shape")
				.setCrs(MGC.getCRS(outputCoordinateSystem))
				.addAttribute("shape_id", String.class)
				.create();

		for(RouteShape shape : shapes) {
			if(shape != null) {
				Collection<Coord> points = shape.getCoordsSorted().values();
				int i = 0;
				Coordinate[] coordinates = new Coordinate[points.size()];
				for(Coord coord : points) {
					coordinates[i++] = MGC.coord2Coordinate(coord);
				}

				SimpleFeature f = ff.createPolyline(coordinates);
				f.setAttribute("shape_id", shape.getId());
				features.add(f);
			}
		}
		ShapeFileWriter.writeGeometries(features, filename);

	}

	public static Map<Id<RouteShape>, RouteShape> readShapesFile(String shapeFile, String outputCoordinateSystem) {
		Map<Id<RouteShape>, RouteShape> shapes = new HashMap<>();
		CoordinateTransformation ct = TransformationFactory.getCoordinateTransformation("WGS84", outputCoordinateSystem);

		CSVReader reader;
		try {
			reader = new CSVReader(new FileReader(shapeFile));
			String[] header = reader.readNext();
			Map<String, Integer> col = getIndices(header, GtfsDefinitions.Files.SHAPES.columns);
			String[] line = reader.readNext();
			while(line != null) {
				Id<RouteShape> shapeId = Id.create(line[col.get(GtfsDefinitions.SHAPE_ID)], RouteShape.class);
				RouteShape currentShape = shapes.get(shapeId);
				if(currentShape == null) {
					currentShape = new GtfsShape(line[col.get(GtfsDefinitions.SHAPE_ID)]);
					shapes.put(shapeId, currentShape);
				}
				Coord point = new Coord(Double.parseDouble(line[col.get(GtfsDefinitions.SHAPE_PT_LON)]), Double.parseDouble(line[col.get(GtfsDefinitions.SHAPE_PT_LAT)]));
				currentShape.addPoint(ct.transform(point), Integer.parseInt(line[col.get(GtfsDefinitions.SHAPE_PT_SEQUENCE)]));
				line = reader.readNext();
			}
			reader.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			throw new RuntimeException("File not found!");
		} catch (ArrayIndexOutOfBoundsException i) {
			throw new RuntimeException("Emtpy line found file!");
		} catch (IOException e) {
			e.printStackTrace();
		}
		return shapes;
	}

	public static List<Coord> transformCoords(CoordinateTransformation ct, List<Coord> coords) {
		List<Coord> transformed = new ArrayList<>();
		for(Coord coord : coords) {
			transformed.add(ct.transform(coord));
		}
		return transformed;
	}
}
