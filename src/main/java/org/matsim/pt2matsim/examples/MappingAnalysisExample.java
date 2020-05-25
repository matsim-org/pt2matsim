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

package org.matsim.pt2matsim.examples;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Network;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt2matsim.plausibility.MappingAnalysis;
import org.matsim.pt2matsim.tools.NetworkTools;
import org.matsim.pt2matsim.tools.ScheduleTools;
import org.matsim.pt2matsim.tools.ShapeTools;
import org.matsim.pt2matsim.tools.lib.RouteShape;

import java.io.File;
import java.util.Map;

/**
 * Analyse the mapping result from PT2MATSimExample with gtfs shapes
 *
 * @author polettif
 */
public class MappingAnalysisExample {

	public static void main(String[] args) {
		String mapperOutput = "example/output/";
		String coordinateSystem = "EPSG:2032";

		// new GtfsFeedImpl("input/addisoncounty-vt-us-gtfs.zip"); // unzips the feed, should have happened in PT2MATSimExample
		Map<Id<RouteShape>, RouteShape> shapes = ShapeTools.readShapesFile("example/input/addisoncounty-vt-us-gtfs/shapes.txt", coordinateSystem);

		// analyse
		TransitSchedule schedule = ScheduleTools.readTransitSchedule(mapperOutput + "addison_schedule.xml");
		Network network = NetworkTools.readNetwork(mapperOutput + "addison_multimodalnetwork.xml.gz");

		new File(mapperOutput + "analysis/").mkdirs();
		MappingAnalysis analysis = new MappingAnalysis(schedule, network, shapes);
		analysis.run();
		analysis.writeAllDistancesCsv(mapperOutput + "analysis/DistancesAll.csv");
		analysis.writeQuantileDistancesCsv(mapperOutput + "analysis/DistancesQuantile.csv");
	}
}