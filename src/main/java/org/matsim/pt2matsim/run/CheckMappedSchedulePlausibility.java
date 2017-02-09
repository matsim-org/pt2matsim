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
import org.matsim.pt2matsim.tools.NetworkTools;
import org.matsim.pt2matsim.tools.ScheduleTools;

import java.io.File;

/**
 * @author polettif
 */
public class CheckMappedSchedulePlausibility {

	protected static final Logger log = Logger.getLogger(PlausibilityCheck.class);


	/**
	 * Performs a plausibility check on the given schedule and network files
	 * and writes the results to the output folder.
	 * @param args	[0] schedule file
	 *              [1] network file
	 *              [2]	coordinate system (of both schedule and network)
	 *              [3]	output folder
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
	 * 	<li>allPlausibilityWarnings.csv: shows all plausibility warnings in a csv file</li>
	 * 	<li>stopfacilities.csv: the number of child stop facilities for all stop facilities as csv</li>
	 * 	<li>stopfacilities_histogram.png: a histogram as png showing the number of child stop facilities</li>
	 * 	<li>shp/warnings/WarningsLoops.shp: Loops warnings as polyline shapefile</li>
	 * 	<li>shp/warnings/WarningsTravelTime.shp: Travel time warnings as polyline shapefile</li>
	 * 	<li>shp/warnings/WarningsDirectionChange.shp: Direction change warnings as polyline shapefile</li>
	 * 	<li>shp/schedule/TransitRoutes.shp: Transit routes of the schedule as polyline shapefile</li>
	 * 	<li>shp/schedule/StopFacilities.shp: Stop Facilities as point shapefile</li>
	 * 	<li>shp/schedule/StopFacilities_refLinks.shp: The stop facilities' reference links as polyline shapefile</li>
	 * </ul>
	 * Shapefiles can be viewed in an GIS, a recommended open source GIS is QGIS. It is also possible to view them in senozon VIA.
	 * However, no line attributes can be displayed or viewed there.
	 * @param scheduleFile the schedule file
	 * @param networkFile network file
	 * @param coordinateSystem A name used by {@link MGC}. Use EPSG:* code to avoid problems.
	 * @param outputFolder the output folder where all csv and shapefiles are written
	 *
	 *
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
		new File(outputFolder+"shp/").mkdir();
		new File(outputFolder+"shp/schedule/").mkdir();
		//noinspection ResultOfMethodCallIgnored
		new File(outputFolder+"shp/warnings/").mkdir();
		check.writeCsv(outputFolder + "allPlausibilityWarnings.csv");
		check.writeResultShapeFiles(outputFolder+"shp/warnings/");

		Schedule2ShapeFile schedule2shp = new Schedule2ShapeFile(schedule, network, coordinateSystem, true);
		schedule2shp.routes2Polylines(outputFolder+"shp/schedule/TransitRoutes.shp");
		schedule2shp.stopFacilities2Shapes(outputFolder+"shp/schedule/StopFacilities.shp", outputFolder+"shp/schedule/StopFacilities_refLinks.shp");

		// stop facility histogram
		StopFacilityHistogram histogram = new StopFacilityHistogram(schedule);
		histogram.createCsv(outputFolder + "stopfacilities.csv");
		histogram.createPng(outputFolder + "stopfacilities_histogram.png");
	}
}
