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

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.utils.collections.MapUtils;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.core.utils.misc.Counter;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt2matsim.gtfs.lib.Shape;

import java.io.FileNotFoundException;
import java.io.UnsupportedEncodingException;
import java.util.*;

/**
 * Analyses the mapping results if the shapes from a gtfs feed are available. Calculates the distances between
 * the mapped route and its corresponding gtfs shape for measureIntervals. The results can be written to a
 * csv file.
 *
 * @author polettif
 */
public class MappingAnalysis {

	private final double measureInterval = 5;

	private static final Logger log = Logger.getLogger(MappingAnalysis.class);

	private final TransitSchedule schedule;
	private final Network network;
	private final Map<Id<TransitLine>, Map<Id<TransitRoute>, Shape>> shapes;
	private final Map<Id<TransitLine>, Map<Id<TransitRoute>, List<Double>>> routeDistances = new HashMap<>();

	public MappingAnalysis(TransitSchedule schedule, Network network, Map<Id<TransitLine>, Map<Id<TransitRoute>, Shape>> shapes) {
		this.schedule = schedule;
		this.network = network;
		this.shapes = shapes;
	}

	public void run() {
		log.info("Start mapping analysis...");

		Counter tr = new Counter("route # ");

		for(TransitLine transitLine : schedule.getTransitLines().values()) {
			for(TransitRoute transitRoute : transitLine.getRoutes().values()) {
				tr.incCounter();

				Shape shape = shapes.get(transitLine.getId()).get(transitRoute.getId());

				List<Link> links = NetworkTools.getLinksFromIds(network, ScheduleTools.getTransitRouteLinkIds(transitRoute));
				double lengthCarryOver = 0;
				for(Link link : links) {
					double lengthOnLink = lengthCarryOver;
					double azimuth = CoordTools.getAzimuth(link.getFromNode().getCoord(), link.getToNode().getCoord());
					double linkLength = CoordUtils.calcEuclideanDistance(link.getFromNode().getCoord(), link.getToNode().getCoord());

					while(lengthOnLink < linkLength) {
						Coord currentPoint = CoordTools.calcNewPoint(link.getFromNode().getCoord(), azimuth, lengthOnLink);

						// look for shortest distance to shape
						double minDistanceToShape = calcMinDistanceToShape(currentPoint, shape);
						MapUtils.getList(transitRoute.getId(), MapUtils.getMap(transitLine.getId(), routeDistances)).add(minDistanceToShape);
						lengthOnLink += measureInterval;
					}
					lengthCarryOver = lengthOnLink - linkLength;
				}
			}
		}
	}

	/**
	 * Calculates the minimal distance from a point to a given shape (from gtfs)
	 */
	private double calcMinDistanceToShape(Coord point, Shape shape) {
		List<Coord> shapePoints = new ArrayList<>(shape.getPoints().values());
		double minDist = Double.MAX_VALUE;
		// look for the minimal distance between the current point and all pairs of shape points
		for(int i=0; i<shapePoints.size()-1; i++) {
			double dist = CoordUtils.distancePointLinesegment(shapePoints.get(i), shapePoints.get(i + 1), point);
			if(dist < minDist) {
				minDist = dist;
			}
		}
		return minDist;
	}


	/**
	 *
	 */
	public void writeQuantileDistancesCsv(String filename) {
		Map<Tuple<Integer, Integer>, String> keyTable = new HashMap<>();
		keyTable.put(new Tuple<>(1, 1), "transitRoute");
		keyTable.put(new Tuple<>(1, 2), "transitLine");
		keyTable.put(new Tuple<>(1, 3), "Q25");
		keyTable.put(new Tuple<>(1, 4), "Q50");
		keyTable.put(new Tuple<>(1, 5), "Q75");
		keyTable.put(new Tuple<>(1, 6), "Q95");
		keyTable.put(new Tuple<>(1, 7), "MAX_DISTANCE");

		int line=2;
		for(TransitLine transitLine : schedule.getTransitLines().values()) {
			for(TransitRoute transitRoute : transitLine.getRoutes().values()) {
				Id<TransitLine> transitLineId = transitLine.getId();
				Id<TransitRoute> transitRouteId = transitRoute.getId();

				List<Double> values = routeDistances.get(transitLineId).get(transitRouteId);
				values.sort(Double::compareTo);
				int n = values.size();

				keyTable.put(new Tuple<>(line, 1), transitLineId.toString());
				keyTable.put(new Tuple<>(line, 2), transitRouteId.toString());
				keyTable.put(new Tuple<>(line, 3), values.get((int) Math.round(n * 0.25)-1).toString());
				keyTable.put(new Tuple<>(line, 4), values.get((int) Math.round(n * 0.50)-1).toString());
				keyTable.put(new Tuple<>(line, 5), values.get((int) Math.round(n * 0.75)-1).toString());
				keyTable.put(new Tuple<>(line, 6), values.get((int) Math.round(n * 0.95)-1).toString());
				keyTable.put(new Tuple<>(line, 7), values.get(n-1).toString());
				line++;
			}
		}

		List<String> csvLines = CsvTools.convertToCsvLines(keyTable, ';');
		try {
			CsvTools.writeToFile(csvLines, filename);
			log.info("Quantiles for distances between mapped routes and gtfs paths written to file " + filename);
		} catch (FileNotFoundException | UnsupportedEncodingException e) {
			e.printStackTrace();
		}
	}

	/**
	 *
	 */
	public void writeAllDistancesCsv(String filename) {
		Map<Tuple<Integer, Integer>, String> keyTable = new HashMap<>();
		keyTable.put(new Tuple<>(1, 1), "transitRoute");
		keyTable.put(new Tuple<>(1, 2), "transitLine");
		keyTable.put(new Tuple<>(1, 3), "distances");

		int line = 2;
		for(Map.Entry<Id<TransitLine>, Map<Id<TransitRoute>, List<Double>>> tl : routeDistances.entrySet()) {
			for(Map.Entry<Id<TransitRoute>, List<Double>> tr : tl.getValue().entrySet()) {
				for(Double dist : tr.getValue()) {
					keyTable.put(new Tuple<>(line, 1), tl.getKey().toString());
					keyTable.put(new Tuple<>(line, 2), tr.getKey().toString());
					keyTable.put(new Tuple<>(line, 3), dist.toString());
					line++;
				}
			}
		}
		List<String> csvLines = CsvTools.convertToCsvLines(keyTable, ';');
		try {
			CsvTools.writeToFile(csvLines, filename);
			log.info("All distances between mapped routes and gtfs paths written to file " + filename);
		} catch (FileNotFoundException | UnsupportedEncodingException e) {
			e.printStackTrace();
		}

	}

}
