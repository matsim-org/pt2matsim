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
import org.matsim.pt2matsim.lib.RouteShape;
import org.matsim.pt2matsim.lib.ShapedSchedule;
import org.matsim.pt2matsim.lib.ShapedTransitSchedule;

import java.io.FileNotFoundException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

	private final ShapedTransitSchedule schedule;
	private final Network network;
	private final Map<Id<TransitLine>, Map<Id<TransitRoute>, List<Double>>> routeDistances = new HashMap<>();
	private final Map<Id<TransitLine>, Map<Id<TransitRoute>, Double>> lengthDiffs = new HashMap<>();

	public MappingAnalysis(ShapedTransitSchedule schedule, Network network) {
		this.schedule = schedule;
		this.network = network;
	}

	public MappingAnalysis(TransitSchedule schedule, Network network, String routeShapeRefFile, String shapeFile, String outputCoordinateSystem) {
		this.schedule = new ShapedSchedule(schedule);
		this.schedule.readShapesFile(shapeFile, outputCoordinateSystem);
		this.schedule.readRouteShapeReferenceFile(routeShapeRefFile);
		this.network = network;

	}

	public void run() {
		log.info("Start mapping analysis...");

		Counter tr = new Counter("route # ");

		for(TransitLine transitLine : schedule.getTransitLines().values()) {
			for(TransitRoute transitRoute : transitLine.getRoutes().values()) {
				tr.incCounter();

				RouteShape shape = schedule.getShape(transitLine.getId(), transitRoute.getId());

				calcRouteShapeDistances(transitLine, transitRoute, shape);

				// todo implement length difference calculations
//				calcLengthDifferences(transitLine, transitRoute, shape);
			}
		}
	}

	private void calcLengthDifferences(TransitLine transitLine, TransitRoute transitRoute, RouteShape shape) {
		double shapeLength = ShapeTools.getShapeLength(shape);
		double routeLength = NetworkTools.calcRouteLength(network.getLinks(), ScheduleTools.getTransitRouteLinkIds(transitRoute), true);

		double diff = Math.abs(shapeLength-routeLength);

		MapUtils.getMap(transitLine.getId(), lengthDiffs).put(transitRoute.getId(), diff);
	}

	/**
	 * Calculate the distances between the route and its corresponding shape
	 */
	private void calcRouteShapeDistances(TransitLine transitLine, TransitRoute transitRoute, RouteShape shape) {
		List<Link> links = NetworkTools.getLinksFromIds(network, ScheduleTools.getTransitRouteLinkIds(transitRoute));
		double lengthCarryOver = 0;
		for(Link link : links) {
			double lengthOnLink = lengthCarryOver;
			double azimuth = CoordTools.getAzimuth(link.getFromNode().getCoord(), link.getToNode().getCoord());
			double linkLength = CoordUtils.calcEuclideanDistance(link.getFromNode().getCoord(), link.getToNode().getCoord());

			while(lengthOnLink < linkLength) {
				Coord currentPoint = CoordTools.calcNewPoint(link.getFromNode().getCoord(), azimuth, lengthOnLink);

				// look for shortest distance to shape
				double minDistanceToShape = ShapeTools.calcMinDistanceToShape(currentPoint, shape);
				MapUtils.getList(transitRoute.getId(), MapUtils.getMap(transitLine.getId(), routeDistances)).add(minDistanceToShape);
				lengthOnLink += measureInterval;
			}
			lengthCarryOver = lengthOnLink - linkLength;
		}
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
		keyTable.put(new Tuple<>(1, 6), "Q85");
		keyTable.put(new Tuple<>(1, 7), "Q95");
		keyTable.put(new Tuple<>(1, 8), "MAX_DISTANCE");

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
				keyTable.put(new Tuple<>(line, 6), values.get((int) Math.round(n * 0.85)-1).toString());
				keyTable.put(new Tuple<>(line, 7), values.get((int) Math.round(n * 0.95)-1).toString());
				keyTable.put(new Tuple<>(line, 8), values.get(n-1).toString());
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
		keyTable.put(new Tuple<>(1, 1), "transitLine");
		keyTable.put(new Tuple<>(1, 2), "transitRoute");
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

	/**
	 * 85%-quantiles are calculated for all transit routes. The 85%-quantile of these quantiles is returned.
	 */
	public double getQ8585() {
		List<Double> q85 = new ArrayList<>();

		for(TransitLine transitLine : schedule.getTransitLines().values()) {
			for(TransitRoute transitRoute : transitLine.getRoutes().values()) {
				Id<TransitLine> transitLineId = transitLine.getId();
				Id<TransitRoute> transitRouteId = transitRoute.getId();

				List<Double> values = routeDistances.get(transitLineId).get(transitRouteId);
				values.sort(Double::compareTo);
				int n = values.size();
				q85.add(values.get((int) Math.round(n * 0.85) - 1));
			}
		}
		q85.sort(Double::compareTo);
		return q85.get((int) Math.round(q85.size() * 0.85) - 1);
	}
}
