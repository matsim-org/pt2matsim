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

package org.matsim.pt2matsim.plausibility;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.utils.collections.MapUtils;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt2matsim.tools.lib.RouteShape;
import org.matsim.pt2matsim.tools.*;

import java.io.FileNotFoundException;
import java.io.UnsupportedEncodingException;
import java.util.*;

/**
 * Analyses the mapping results if the shapes from a gtfs feed are available. Calculates the distances between
 * the mapped transitRoute and its corresponding gtfs shape for measureIntervals. The results can be written to a
 * csv file.
 * <p>
 * The ids of the shapes must be set in each transit transitRoute description with the prefix "shapeId:"
 *
 * @author polettif
 */
public class MappingAnalysis {

	private static final double MEASURE_INTERVAL = 1;

	private static final Logger log = Logger.getLogger(MappingAnalysis.class);
	private final TransitSchedule schedule;
	private final Map<Id<RouteShape>, RouteShape> shapes;
	private final Network network;

	private final Map<Tuple<Id<TransitLine>, Id<TransitRoute>>, List<Double>> routeDistances = new HashMap<>();
	private final Map<Id<TransitLine>, Map<Id<TransitRoute>, Double>> lengthRatios = new HashMap<>();
	private final Map<Tuple<Id<TransitLine>, Id<TransitRoute>>, Id<RouteShape>> shapeAssignment = new HashMap<>();

	private final Set<Tuple<Id<TransitLine>, Id<TransitRoute>>> noAnalysis = new HashSet<>();

	public MappingAnalysis(TransitSchedule schedule, Network network, Map<Id<RouteShape>, RouteShape> shapes) {
		this.schedule = schedule;
		this.shapes = shapes;
		this.network = network;
	}

	public void run() {
		log.info("Start mapping analysis...");
		// initiate threads
		int numThreads = 8;
		MappingAnalyser[] mappingAnalyser = new MappingAnalyser[numThreads];
		for(int i = 0; i < numThreads; i++) {
			mappingAnalyser[i] = new MappingAnalyser();
		}

		int t = 0; // spread on threads
		for(TransitLine transitLine : schedule.getTransitLines().values()) {
			for(TransitRoute transitRoute : transitLine.getRoutes().values()) {
				Id<RouteShape> shapeId = ScheduleTools.getShapeId(transitRoute);

				RouteShape shape = shapes.get(shapeId);

				if(shape != null) {
					shapeAssignment.put(new Tuple<>(transitLine.getId(), transitRoute.getId()), shape.getId());
					mappingAnalyser[t++].addToQueue(transitLine, transitRoute, shape);
				} else {
					noAnalysis.add(new Tuple<>(transitLine.getId(), transitRoute.getId()));
				}

				if(t >= numThreads) t = 0;
			}
		}

		// calculate distances
		Thread[] threads = new Thread[numThreads];
		for(int i = 0; i < numThreads; i++) {
			threads[i] = new Thread(mappingAnalyser[i]);
			threads[i].start();
		}
		for(Thread thread : threads) {
			try {
				thread.join();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}

		if(noAnalysis.size() > 0) {
			log.warn("No shapes defined for the following transit routes!");
			for(Tuple<Id<TransitLine>, Id<TransitRoute>> n : noAnalysis) {
				log.info("   > transit transitLine: " + n.getFirst() + ", transit transitRoute: " + n.getSecond());
			}
		}

		log.info("Mapping analysis finished...");
		log.info(">>> Ldiff: " + String.format("%.1f", Math.sqrt(getAverageSquaredLengthRatio()) * 100) + "%");
		log.info(">>> Q8585: " + String.format("%.2f", getQ8585()));
	}

	/**
	 *
	 */
	public void writeQuantileDistancesCsv(String filename) {
		Map<Tuple<Integer, Integer>, String> keyTable = new HashMap<>();
		keyTable.put(new Tuple<>(1, 1), "transitLine");
		keyTable.put(new Tuple<>(1, 2), "transitRoute");
		keyTable.put(new Tuple<>(1, 3), "shape");
		keyTable.put(new Tuple<>(1, 4), "lengthRatio");
		keyTable.put(new Tuple<>(1, 5), "Q25");
		keyTable.put(new Tuple<>(1, 6), "Q50");
		keyTable.put(new Tuple<>(1, 7), "Q75");
		keyTable.put(new Tuple<>(1, 8), "Q85");
		keyTable.put(new Tuple<>(1, 9), "Q95");
		keyTable.put(new Tuple<>(1, 10), "MAX_DISTANCE");

		int line = 2;
		for(TransitLine transitLine : schedule.getTransitLines().values()) {
			for(TransitRoute transitRoute : transitLine.getRoutes().values()) {
				Id<TransitLine> transitLineId = transitLine.getId();
				Id<TransitRoute> transitRouteId = transitRoute.getId();

				if(!noAnalysis.contains(new Tuple<>(transitLine.getId(), transitRoute.getId()))) {
					List<Double> values = routeDistances.get(new Tuple<>(transitLineId, transitRouteId));
					values.sort(Double::compareTo);
					Double lengthRatio = lengthRatios.get(transitLineId).get(transitRouteId);
					int n = values.size();

					keyTable.put(new Tuple<>(line, 1), transitLineId.toString());
					keyTable.put(new Tuple<>(line, 2), transitRouteId.toString());
					keyTable.put(new Tuple<>(line, 3), shapeAssignment.get(new Tuple<>(transitLineId, transitRouteId)).toString());
					keyTable.put(new Tuple<>(line, 4), lengthRatio.toString());
					keyTable.put(new Tuple<>(line, 5), values.get((int) (n * 0.25)).toString());
					keyTable.put(new Tuple<>(line, 6), values.get((int) (n * 0.50)).toString());
					keyTable.put(new Tuple<>(line, 7), values.get((int) (n * 0.75)).toString());
					keyTable.put(new Tuple<>(line, 8), values.get((int) (n * 0.85)).toString());
					keyTable.put(new Tuple<>(line, 9), values.get((int) (n * 0.95)).toString());
					keyTable.put(new Tuple<>(line, 10), values.get(n - 1).toString());
					line++;
				}
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
	 * Writes all measured distances for all transit transitRoute to a csv file. Each measured distance is written to a new file
	 * which tends to inflate files considerably.
	 */
	public void writeAllDistancesCsv(String filename) {
		Map<Tuple<Integer, Integer>, String> keyTable = new HashMap<>();
		keyTable.put(new Tuple<>(1, 1), "transitLine");
		keyTable.put(new Tuple<>(1, 2), "transitRoute");
		keyTable.put(new Tuple<>(1, 3), "distances");

		int line = 2;
		for(Map.Entry<Tuple<Id<TransitLine>, Id<TransitRoute>>, List<Double>> e : routeDistances.entrySet()) {
			for(Double dist : e.getValue()) {
				keyTable.put(new Tuple<>(line, 1), e.getKey().getFirst().toString());
				keyTable.put(new Tuple<>(line, 2), e.getKey().getSecond().toString());
				keyTable.put(new Tuple<>(line, 3), dist.toString());
				line++;
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
	 * 85%-quantile distances are calculated for all transit routes.
	 * The 85%-quantile of these quantiles is returned. This value can be used
	 * to quickly compare different config parameters or algorithms.
	 */
	public double getQ8585() {
		return getQQ(0.85);
	}

	public double getQQ(double p) {
		List<Double> q = new ArrayList<>();

		for(TransitLine transitLine : schedule.getTransitLines().values()) {
			for(TransitRoute transitRoute : transitLine.getRoutes().values()) {
				Id<TransitLine> transitLineId = transitLine.getId();
				Id<TransitRoute> transitRouteId = transitRoute.getId();

				if(!noAnalysis.contains(new Tuple<>(transitLine.getId(), transitRoute.getId()))) {
					List<Double> values = routeDistances.get(new Tuple<>(transitLineId, transitRouteId));
					values.sort(Double::compareTo);
					q.add(values.get((int) (values.size() * p)));
				}
			}
		}
		q.sort(Double::compareTo);
		return q.get((int) (q.size() * p));
	}

	/**
	 * @return the average of all squared length differences between mapped path and shape
	 */
	public double getAverageSquaredLengthRatio() {
		double sum = 0;
		double n = 0;
		for(Map.Entry<Id<TransitLine>, Map<Id<TransitRoute>, Double>> tl : lengthRatios.entrySet()) {
			for(Map.Entry<Id<TransitRoute>, Double> tr : tl.getValue().entrySet()) {
				double val = lengthRatios.get(tl.getKey()).get(tr.getKey());
				sum += val * val;
				n++;
			}
		}
		return sum / n;
	}

	/**
	 * @return the quantiles for the distance between transitRoute and shape for the given transit transitRoute
	 */
	TreeMap<Integer, Double> getQuantiles(Id<TransitLine> transitLineId, Id<TransitRoute> transitRouteId) {
		List<Double> values = routeDistances.get(new Tuple<>(transitLineId, transitRouteId));
		values.sort(Double::compareTo);
		int n = values.size();

		TreeMap<Integer, Double> quantiles = new TreeMap<>();
		quantiles.put(0, values.get(0));
		quantiles.put(25, values.get((int) (n * 0.25)));
		quantiles.put(50, values.get((int) (n * 0.50)));
		quantiles.put(75, values.get((int) (n * 0.75)));
		quantiles.put(85, values.get((int) (n * 0.85)));
		quantiles.put(95, values.get((int) (n * 0.95)));
		quantiles.put(100, values.get(n - 1));

		return quantiles;
	}

	/**
	 * @return the ratio of the mapped path length and the shape length
	 */
	double getLengthRatio(Id<TransitLine> transitLineId, Id<TransitRoute> transitRouteId) {
		return lengthRatios.get(transitLineId).get(transitRouteId);
	}

	/**
	 * Calculates the distance between a transit route and shape
	 */
	private class MappingAnalyser implements Runnable {

		private Map<Tuple<TransitLine, TransitRoute>, RouteShape> queue = new HashMap<>();

		public void addToQueue(TransitLine transitLine, TransitRoute transitRoute, RouteShape shape) {
			if(queue.put(new Tuple<>(transitLine, transitRoute), shape) != null) {
				throw new RuntimeException(transitRoute.getId() + " already added to queue");
			}
		}

		@Override
		public void run() {
			for(Map.Entry<Tuple<TransitLine, TransitRoute>, RouteShape> entry : queue.entrySet()) {
				calcRouteShapeDistances(entry.getKey().getFirst(), entry.getKey().getSecond(), entry.getValue());
				calcLengthDiffRatios(entry.getKey().getFirst(), entry.getKey().getSecond(), entry.getValue());
			}
		}

		/**
		 * Calculate the distances between the transitRoute and its corresponding shape
		 */
		private void calcRouteShapeDistances(TransitLine transitLine, TransitRoute transitRoute, RouteShape shape) {
			List<Link> links = NetworkTools.getLinksFromIds(network, ScheduleTools.getTransitRouteLinkIds(transitRoute));
			// we need an equivalent number of measurements for the whole transitRoute
			double lengthOnLink = 0;
			for(Link link : links) {
				double azimuth = CoordTools.getAzimuth(link.getFromNode().getCoord(), link.getToNode().getCoord());
				double linkLength = CoordUtils.calcEuclideanDistance(link.getFromNode().getCoord(), link.getToNode().getCoord());

				while(lengthOnLink < linkLength) {
					Coord currentPoint = CoordTools.calcNewPoint(link.getFromNode().getCoord(), azimuth, lengthOnLink);

					// look for shortest distance to shape
					double minDistanceToShape = ShapeTools.calcMinDistanceToShape(currentPoint, shape);
					addMinDistance(transitLine.getId(), transitRoute.getId(), minDistanceToShape);
					lengthOnLink += MEASURE_INTERVAL;
				}
				lengthOnLink = lengthOnLink - linkLength;
			}
		}


		/**
		 * Calculate the ratio between mapped path and shape.
		 */
		private void calcLengthDiffRatios(TransitLine transitLine, TransitRoute transitRoute, RouteShape shape) {
			double shapeLength = ShapeTools.getShapeLength(shape);
			double routeLength = 0;
			try {
				routeLength = NetworkTools.calcRouteLength(network, transitRoute, true);
			} catch (Exception e) {
				log.warn("Transit transitRoute " + transitRoute.getId() + " on transit transitLine " + transitLine.getId() + " is inconsistent, links not connected");
			}
			double ratio = (routeLength - shapeLength) / shapeLength;
			putLengthRatio(transitLine.getId(), transitRoute.getId(), ratio);
		}
	}

	private synchronized void putLengthRatio(Id<TransitLine> lineId, Id<TransitRoute> routeId, double value) {
		MapUtils.getMap(lineId, this.lengthRatios).put(routeId, value);
	}

	private synchronized void addMinDistance(Id<TransitLine> lineId, Id<TransitRoute> routeId, double minDistanceToShape) {
		MapUtils.getList(new Tuple<>(lineId, routeId), this.routeDistances).add(minDistanceToShape);
	}


}
