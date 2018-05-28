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

package org.matsim.pt2matsim.run;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.utils.geometry.geotools.MGC;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.utils.TransitScheduleValidator;
import org.matsim.pt2matsim.plausibility.PlausibilityCheck;
import org.matsim.pt2matsim.plausibility.StopFacilityHistogram;
import org.matsim.pt2matsim.run.gis.Network2Geojson;
import org.matsim.pt2matsim.run.gis.Schedule2Geojson;
import org.matsim.pt2matsim.tools.NetworkTools;
import org.matsim.pt2matsim.tools.ScheduleTools;

import java.io.File;

/**
 * @author polettif
 */
public final class CheckMappedSchedulePlausibility {

	protected static final Logger log = Logger.getLogger(PlausibilityCheck.class);

	/**
	 * Performs a plausibility check on the given schedule and network files
	 * and writes the results to the output folder.
	 *
	 * @param args [0] schedule file
	 *             [1] network file
	 *             [2] coordinate system (of both schedule and network)
	 *             [3] output folder
	 */
	public static void main(final String[] args) {
		if(args.length == 4) {
			run(args[0], args[1], args[2], args[3]);
		} else {
			throw new IllegalArgumentException(args.length + " instead of 4 arguments given");
		}
	}

	/**
	 * Performs a plausibility check on the given schedule and network files
	 * and writes the results to the output folder. The following files are
	 * created in the output folder:
	 * <ul>
	 * <li>allPlausibilityWarnings.csv: shows all plausibility warnings in a csv file</li>
	 * <li>stopfacilities.csv: the number of child stop facilities for all stop facilities as csv</li>
	 * <li>stopfacilities_histogram.png: a histogram as png showing the number of child stop facilities</li>
	 * <li>plausibilityWarnings.geojson: Contains all warnings for groups of links</li>
	 * <li>schedule_TransitRoutes.geojson: Transit routes of the schedule as lines</li>
	 * <li>schedule_TtopFacilities.geojson: Stop Facilities as points</li>
	 * <li>schedule_StopFacilities_refLinks.geojson: The stop facilities' reference links as lines</li>
	 * <li>network.geojson: Network as geojson file containing nodes and links</li>
	 * </ul>
	 *
	 * Geojson can be viewed in an GIS, a recommended open source GIS is QGIS.
	 *
	 * @param scheduleFile     the schedule file
	 * @param networkFile      the network file
	 * @param coordinateSystem A name used by {@link MGC}. Use EPSG:* code to avoid problems.
	 * @param outputFolder     the output folder where all files are written to
	 */
	public static void run(String scheduleFile, String networkFile, String coordinateSystem, String outputFolder) {
		PlausibilityCheck.setLogLevels();

		log.info("Reading schedule...");
		TransitSchedule schedule = ScheduleTools.readTransitSchedule(scheduleFile);
		log.info("Reading network...");
		Network network = NetworkTools.readNetwork(networkFile);

		log.info("Run TransitScheduleValidator...");
		TransitScheduleValidator.ValidationResult v = TransitScheduleValidator.validateAll(schedule, network);
		TransitScheduleValidator.printResult(v);

		log.info("Start plausibility check...");
		PlausibilityCheck check = new PlausibilityCheck(schedule, network, coordinateSystem);
		check.runCheck();

		if(!outputFolder.endsWith("/")) {
			outputFolder = outputFolder + "/";
		}

		new File(outputFolder).mkdir();
		check.writeCsv(outputFolder + "allPlausibilityWarnings.csv");
		check.writeResultsGeojson( outputFolder + "plausibilityWarnings.geojson");

		// "legacy" shapefile output
		if(false) {
			new File(outputFolder + "warnings_shp/").mkdir();
			check.writeResultShapeFiles(outputFolder + "warnings_shp/");
		}

		// transit schedule as geojson
		Schedule2Geojson schedule2geojson = new Schedule2Geojson(coordinateSystem, schedule, network);
		schedule2geojson.writeTransitRoutes(outputFolder + "schedule_TransitRoutes.geojson");
		schedule2geojson.writeStopFacilities(outputFolder + "schedule_StopFacilities.geojson");
		schedule2geojson.writeStopRefLinks(outputFolder + "schedule_StopFacilities_refLinks.geojson");

		// stop facility histogram
		StopFacilityHistogram histogram = new StopFacilityHistogram(schedule);
		histogram.createCsv(outputFolder + "stopfacilities.csv");
		histogram.createPng(outputFolder + "stopfacilities_histogram.png");

		// write network
		Network2Geojson.run(network, outputFolder + "network.geojson");

		check.printStatisticsLog();
	}

}
