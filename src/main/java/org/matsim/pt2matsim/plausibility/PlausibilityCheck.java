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

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.utils.collections.CollectionUtils;
import org.matsim.core.utils.collections.MapUtils;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.core.utils.geometry.geotools.MGC;
import org.matsim.core.utils.gis.PolylineFeatureFactory;
import org.matsim.core.utils.gis.ShapeFileWriter;
import org.matsim.core.utils.io.MatsimFileTypeGuesser;
import org.matsim.core.utils.io.MatsimXmlParser;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitRouteStop;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt2matsim.plausibility.log.*;
import org.matsim.pt2matsim.tools.*;
import org.opengis.feature.simple.SimpleFeature;

import java.io.FileNotFoundException;
import java.io.UnsupportedEncodingException;
import java.util.*;

import static org.matsim.pt2matsim.tools.ScheduleTools.getTransitRouteLinkIds;

/**
 * Performs a plausibility check on the given schedule
 * and network. Checks for three implausibilities:
 * <ul>
 *     <li>loops</li>
 *     <li>travel time</li>
 *     <li>direction changes</li>
 * </ul>
 *
 * @author polettif
 */
public class PlausibilityCheck {

	protected static final Logger log = Logger.getLogger(PlausibilityCheck.class);

	public static final String CsvSeparator = ",";

	private static final double PI = Math.PI;

	public static final String TRAVEL_TIME_WARNING = "TravelTimeWarning";
	public static final String LOOP_WARNING = "LoopWarning";
	public static final String DIRECTION_CHANGE_WARNING = "DirectionChangeWarning";

	private final Set<PlausibilityWarning> allWarnings = new HashSet<>();
	private final Map<TransitLine, Map<TransitRoute, Set<PlausibilityWarning>>> warningsSchedule = new HashMap<>();
	private final Map<List<Id<Link>>, Set<PlausibilityWarning>> warningsLinkIds = new HashMap<>();
	private final Map<Id<Link>, Set<PlausibilityWarning>> warningsLinks = new HashMap<>();

	private Map<String, Double> thresholds;

	private final TransitSchedule schedule;
	private final Network network;
	private final String coordinateSystem;

	/**
	 * Allowed travel time difference between actual and scheduled time in seconds
	 */
	private double ttRange;


	/**
	 * Constructor
	 */
	public PlausibilityCheck(TransitSchedule schedule, Network network, String coordinateSystem) {
		this.schedule = schedule;
		this.network = network;
		this.coordinateSystem = coordinateSystem;

		this.thresholds = new HashMap<>();
		this.thresholds.put("bus", 0.7 * PI);
		this.thresholds.put("rail", 0.3 * PI);
		this.ttRange = 60;
	}

	/**
	 * Performs the plausibility check on the schedule
	 */
	public void runCheck() {
		AbstractPlausibilityWarning.setNetwork(network);

		for(TransitLine transitLine : this.schedule.getTransitLines().values()) {
			for(TransitRoute transitRoute : transitLine.getRoutes().values()) {

				Double directionChangeThreshold = thresholds.get(transitRoute.getTransportMode());

				Iterator<TransitRouteStop> stopsIterator = transitRoute.getStops().iterator();

				List<Link> links = NetworkTools.getLinksFromIds(network, getTransitRouteLinkIds(transitRoute));
				Map<Node, Tuple<Link, Link>> nodesInRoute = new HashMap<>();
				Set<List<Id<Link>>> loops = new HashSet<>();

				TransitRouteStop previousStop = stopsIterator.next();
				TransitRouteStop nextStop = stopsIterator.next();
				double ttActual = 0;
				double departTime = previousStop.getDepartureOffset();

				for(int i = 0; i < links.size() - 2; i++) {
					Link linkFrom = links.get(i);
					Link linkTo = links.get(i + 1);

					// travel time check
					ttActual += linkFrom.getLength() / linkFrom.getFreespeed();
					if(nextStop.getStopFacility().getLinkId().equals(linkTo.getId())) {
						double ttSchedule = nextStop.getArrivalOffset() - departTime;
						double ttScheduleRange = ttSchedule + ttRange;
						if(ttActual > ttScheduleRange && ttSchedule > 0) {
							PlausibilityWarning warning = new TravelTimeWarning(transitLine, transitRoute, previousStop, nextStop, ttActual, ttSchedule);
							addWarningToContainers(warning);
						}
						// reset
						ttActual = 0;
						previousStop = nextStop;
						departTime = previousStop.getDepartureOffset();
						if(!nextStop.equals(transitRoute.getStops().get(transitRoute.getStops().size() - 1))) {
							nextStop = stopsIterator.next();
						}
					}

					// loopcheck
					Tuple<Link, Link> tuple = nodesInRoute.put(linkFrom.getToNode(), new Tuple<>(linkFrom, linkTo));
					if(tuple != null && !linkFrom.equals(tuple.getSecond())) {
						loops.add(ScheduleTools.getLoopSubRouteLinkIds(transitRoute, tuple.getSecond().getId(), linkFrom.getId()));
					}

					// angle check (check if one link has length 0)
					if(directionChangeThreshold != null) {
						double angleDiff = CoordTools.getAzimuthDiff(linkFrom, linkTo);
						if(Math.abs(angleDiff) > directionChangeThreshold && linkFrom.getLength() > 0 && linkTo.getLength() > 0 && angleDiff != PI) {
							PlausibilityWarning warning = new DirectionChangeWarning(transitLine, transitRoute, linkFrom, linkTo, directionChangeThreshold, angleDiff);
							addWarningToContainers(warning);
						}
					}
				}

				// get "loop" that are part of a bigger loop
				Set<List<Id<Link>>> subsetLoops = new HashSet<>();
				for(List<Id<Link>> loop1 : loops) {
					for(List<Id<Link>> loop2 : loops) {
						if(!loop1.equals(loop2) && MiscUtils.listIsSubset(loop1, loop2)) {
							subsetLoops.add(loop1);
						}
					}
				}
				// add LoopWarning
				for(List<Id<Link>> loop : loops) {
					if(!subsetLoops.contains(loop)) {
						PlausibilityWarning warning = new LoopWarning(transitLine, transitRoute, loop);
						addWarningToContainers(warning);
					}
				}
			}
		}
	}

	/**
	 * Writes all warnings to a csv file
	 */
	public void writeCsv(String outputFile) {
		List<String> csvLines = new ArrayList<>();
		csvLines.add(AbstractPlausibilityWarning.CSV_HEADER);
		for(PlausibilityWarning w : allWarnings) {
			csvLines.add(w.getCsvLine());
		}
		try {
			log.info("Writing warnings to csv file " +outputFile +" ...");
			CsvTools.writeToFile(csvLines, outputFile);
		} catch (FileNotFoundException | UnsupportedEncodingException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Writes all warnings to several shape files in the given folder
	 */
	public void writeResultShapeFiles(String outputPath) {
		log.info("Writing warnings shapefiles in folder " + outputPath + " ...");

		Collection<SimpleFeature> traveltTimeWarningsFeatures = new ArrayList<>();
		Collection<SimpleFeature> loopWarningsFeatures = new ArrayList<>();
		Collection<SimpleFeature> directionChangeWarningsFeatures = new ArrayList<>();

		PolylineFeatureFactory travelTimeWarningsFF = new PolylineFeatureFactory.Builder()
				.setName("TravelTimeWarnings")
				.setCrs(MGC.getCRS(coordinateSystem))
				.addAttribute("warningIds", String.class)
				.addAttribute("routeIds", String.class)
				.addAttribute("linkIds", String.class)
				.addAttribute("diff [s]", Double.class)
				.addAttribute("diff [%]", Double.class)
				.addAttribute("expected", Double.class)
				.addAttribute("actual", Double.class)
				.create();

		PolylineFeatureFactory loopWarningsFF = new PolylineFeatureFactory.Builder()
				.setName("LoopWarnings")
				.setCrs(MGC.getCRS(coordinateSystem))
				.addAttribute("warningIds", String.class)
				.addAttribute("routeIds", String.class)
				.addAttribute("linkIds", String.class)
				.create();

		PolylineFeatureFactory directionChangeWarnings = new PolylineFeatureFactory.Builder()
				.setName("DirectionChangeWarnings")
				.setCrs(MGC.getCRS(coordinateSystem))
				.addAttribute("warningIds", String.class)
				.addAttribute("routeIds", String.class)
				.addAttribute("linkIds", String.class)
				.addAttribute("diff [rad]", String.class)
				.addAttribute("diff [gon]", String.class)
				.create();

		// route through all linkIdLists
		for(Map.Entry<List<Id<Link>>, Set<PlausibilityWarning>> e : warningsLinkIds.entrySet()) {
			boolean createLoopFeature = false;
			boolean createTravelTimeFeature = false;
			boolean createDirectionChangeFeature = false;

			double diff = -1, diffPerc = -1, ttExpected = -1, ttActual = -1, azDiff = 0.0;

			Set<Id<PlausibilityWarning>> warningIds = new HashSet<>();
			Set<String> routeIds = new HashSet<>();

			for(PlausibilityWarning w : e.getValue()) {
				// Travel Time Warnings
				if(w instanceof TravelTimeWarning) {
					createTravelTimeFeature = true;
					if(w.getExpected()/w.getActual() > diff) {
						diff = w.getDifference();
						ttActual = w.getActual();
						ttExpected = w.getExpected();
						diffPerc = ttActual / ttExpected - 1;
					}
					warningIds.add(w.getId());
					routeIds.add(w.getTransitLine().getId() + ":" + w.getTransitRoute().getId());
				}

				// Direction Change Warnings
				if(w instanceof DirectionChangeWarning) {
					createDirectionChangeFeature = true;
					warningIds.add(w.getId());
					routeIds.add(w.getTransitLine().getId() + ":" + w.getTransitRoute().getId());
					azDiff = w.getDifference();
				}

				// Loop Warnings
				if(w instanceof LoopWarning) {
					createLoopFeature = true;
					warningIds.add(w.getId());
					routeIds.add(w.getTransitLine().getId() + ":" + w.getTransitRoute().getId());
				}
			}

			// Travel Time Warnings
			if(createTravelTimeFeature) {
				SimpleFeature f = travelTimeWarningsFF.createPolyline(ShapeTools.linkIdList2Coordinates(network, e.getKey()));
				f.setAttribute("warningIds", CollectionUtils.idSetToString(warningIds));
				f.setAttribute("routeIds", CollectionUtils.setToString(routeIds));
				f.setAttribute("linkIds", CollectionUtils.idSetToString(new HashSet<>(e.getKey())));
				f.setAttribute("diff [s]", diff);
				f.setAttribute("diff [%]", diffPerc);
				f.setAttribute("expected", ttExpected);
				f.setAttribute("actual", ttActual);
				traveltTimeWarningsFeatures.add(f);
			}

			// Direction Change Warning
			if(createDirectionChangeFeature) {
				SimpleFeature f = directionChangeWarnings.createPolyline(ShapeTools.linkIdList2Coordinates(network, e.getKey()));
				f.setAttribute("warningIds", CollectionUtils.idSetToString(warningIds));
				f.setAttribute("routeIds", CollectionUtils.setToString(routeIds));
				f.setAttribute("linkIds", CollectionUtils.idSetToString(new HashSet<>(e.getKey())));
				f.setAttribute("diff [rad]", azDiff);
				f.setAttribute("diff [gon]", 200*azDiff/Math.PI);
				directionChangeWarningsFeatures.add(f);
			}

			// Loop Warnings
			if(createLoopFeature) {
				SimpleFeature f = loopWarningsFF.createPolyline(ShapeTools.linkIdList2Coordinates(network, e.getKey()));
				f.setAttribute("warningIds", CollectionUtils.idSetToString(warningIds));
				f.setAttribute("routeIds", CollectionUtils.setToString(routeIds));
				f.setAttribute("linkIds", CollectionUtils.idSetToString(new HashSet<>(e.getKey())));
				loopWarningsFeatures.add(f);
			}
		}

		if(traveltTimeWarningsFeatures.size() > 0) 		ShapeFileWriter.writeGeometries(traveltTimeWarningsFeatures, outputPath + TRAVEL_TIME_WARNING + "s.shp");
		if(directionChangeWarningsFeatures.size() > 0) 	ShapeFileWriter.writeGeometries(directionChangeWarningsFeatures, outputPath + DIRECTION_CHANGE_WARNING + "s.shp");
		if(loopWarningsFeatures.size() > 0) 			ShapeFileWriter.writeGeometries(loopWarningsFeatures, outputPath + LOOP_WARNING + "s.shp");
	}

	/**
	 * Adds a warning object to the different data containers.
	 */
	private void addWarningToContainers(PlausibilityWarning warning) {
		allWarnings.add(warning);
		MapUtils.getSet(warning.getTransitRoute(), MapUtils.getMap(warning.getTransitLine(), this.warningsSchedule)).add(warning);
		MapUtils.getSet(warning.getLinkIds(), warningsLinkIds).add(warning);

		for(Id<Link> linkId : warning.getLinkIds()) {
			MapUtils.getSet(linkId, warningsLinks).add(warning);
		}
	}

	public static void setLogLevels() {
		Logger.getLogger(MGC.class).setLevel(Level.ERROR);
		Logger.getLogger(MatsimFileTypeGuesser.class).setLevel(Level.ERROR);
		Logger.getLogger(Network.class).setLevel(Level.ERROR);
		Logger.getLogger(Node.class).setLevel(Level.ERROR);
		Logger.getLogger(Link.class).setLevel(Level.ERROR);
		Logger.getLogger(MatsimXmlParser.class).setLevel(Level.ERROR);
	}
}