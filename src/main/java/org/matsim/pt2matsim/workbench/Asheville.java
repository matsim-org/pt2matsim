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

package org.matsim.pt2matsim.workbench;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Network;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt2matsim.config.OsmConverterConfigGroup;
import org.matsim.pt2matsim.config.PublicTransitMappingConfigGroup;
import org.matsim.pt2matsim.gtfs.GtfsConverter;
import org.matsim.pt2matsim.lib.RouteShape;
import org.matsim.pt2matsim.mapping.PTMapper;
import org.matsim.pt2matsim.plausibility.MappingAnalysis;
import org.matsim.pt2matsim.run.Gtfs2TransitSchedule;
import org.matsim.pt2matsim.run.Osm2MultimodalNetwork;
import org.matsim.pt2matsim.run.PublicTransitMapper;
import org.matsim.pt2matsim.run.shp.Schedule2ShapeFile;
import org.matsim.pt2matsim.tools.NetworkTools;
import org.matsim.pt2matsim.tools.ScheduleTools;
import org.matsim.pt2matsim.tools.ShapeTools;

import java.util.Map;

/**
 * @author polettif
 */
public class Asheville {

	public static final String EPSG = "EPSG:3631";
	public static final String inputNetworkFile = "network/asheville.xml.gz";
	public static final String inputScheduleFile = "schedule/schedule_unmapped.xml.gz";
	public static final String outputNetworkFile = "output/asheville_network.xml.gz";
	public static final String outputScheduleFile = "output/asheville_schedule.xml.gz";

	public static void main(String[] args) {
//		convertOsm();
//		convertGtfs();
		runMapping();
		analysis();
	}

	public static void convertOsm() {
		OsmConverterConfigGroup config = OsmConverterConfigGroup.createDefaultConfig();
		config.setKeepPaths(true);
		config.setOsmFile("network/asheville.osm");
		config.setOutputCoordinateSystem(EPSG);
		config.setOutputNetworkFile(inputNetworkFile);

		Osm2MultimodalNetwork.run(config);
	}

	public static void convertGtfs() {
		Gtfs2TransitSchedule.run("gtfs/", GtfsConverter.DAY_WITH_MOST_SERVICES, EPSG, inputScheduleFile, "schedule/vhcls.xml.gz");
	}

	public static void runMapping() {
		PublicTransitMappingConfigGroup config = PublicTransitMappingConfigGroup.createDefaultConfig();
		config.setNumOfThreads(6);

		TransitSchedule schedule = ScheduleTools.readTransitSchedule(inputScheduleFile);
		Network network = NetworkTools.readNetwork(inputNetworkFile);

		PTMapper.run(config, schedule, network);

		ScheduleTools.writeTransitSchedule(schedule, outputScheduleFile);
		NetworkTools.writeNetwork(network, outputNetworkFile);

		Schedule2ShapeFile.run(EPSG, "output/shp/", schedule, network);
	}

	private static void analysis() {
		TransitSchedule schedule = ScheduleTools.readTransitSchedule(outputScheduleFile);
		Network network = NetworkTools.readNetwork(outputNetworkFile);
		Map<Id<RouteShape>, RouteShape> shapes = ShapeTools.readShapesFile("gtfs/shapes.txt", EPSG);
		ShapeTools.writeESRIShapeFile(shapes.values(), EPSG, "output/shp/gtfs.shp");

		MappingAnalysis analysis = new MappingAnalysis(schedule, network, shapes);
		analysis.run();
		System.out.format("\n>>> Q8585: %.3f\n", analysis.getQ8585());

		analysis.writeQuantileDistancesCsv("output/analysis/quantiles.csv");
	}

}
