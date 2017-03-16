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

import org.matsim.api.core.v01.network.Network;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt2matsim.config.PublicTransitMappingConfigGroup;
import org.matsim.pt2matsim.gtfs.GtfsConverter;
import org.matsim.pt2matsim.gtfs.GtfsFeed;
import org.matsim.pt2matsim.gtfs.GtfsFeedImpl;
import org.matsim.pt2matsim.mapping.PTMapper;
import org.matsim.pt2matsim.plausibility.MappingAnalysis;
import org.matsim.pt2matsim.tools.NetworkTools;
import org.matsim.pt2matsim.tools.ScheduleTools;
import org.matsim.pt2matsim.tools.ShapeTools;
import org.matsim.vehicles.VehicleUtils;
import org.matsim.vehicles.Vehicles;

import java.io.File;
import java.io.IOException;

/**
 * @author polettif
 */
public class MappingAnalysisTest {

	private TransitSchedule schedule;
	private Vehicles vehicles;
	private Network network;
	private GtfsConverter gtfsConverter;
	private String coordinateSystem;

	private String input = "test/analysis/";

	public static void main(String[] args) throws Exception {
		MappingAnalysisTest obj = new MappingAnalysisTest();
		obj.prepare();
		obj.runMapping();
		obj.analysis();
	}


	public void prepare() throws IOException {

		network = NetworkTools.readNetwork(input + "network/addison.xml.gz");

		coordinateSystem = "EPSG:2032";

		// convert schedule
		schedule = ScheduleTools.createSchedule();
		vehicles = VehicleUtils.createVehiclesContainer();
		GtfsFeed gtfsFeed = new GtfsFeedImpl(input + "addisoncounty-vt-us-gtfs/");

		gtfsConverter = new GtfsConverter(gtfsFeed);
		gtfsConverter.convert(GtfsConverter.ALL_SERVICE_IDS, coordinateSystem, schedule, vehicles);

//		ExtractDebugSchedule.run(schedule, "SBSB_1437", "59468A1158B4286");

		ScheduleTools.writeTransitSchedule(gtfsConverter.getSchedule(), input + "mts/schedule_unmapped.xml.gz");

		// read network
		/*convert from osm
		OsmConverterConfigGroup osmConfig = OsmConverterConfigGroup.createDefaultConfig();
		osmConfig.setOutputCoordinateSystem("EPSG:2032");
		osmConfig.setOsmFile(input+"osm/addison.osm");
		osmConfig.setOutputNetworkFile(input+"network/addison.xml.gz");
		osmConfig.setMaxLinkLength(20);

		new OsmMultimodalNetworkConverter(osmConfig).run();
		*/

	}

	public void runMapping() {
		PublicTransitMappingConfigGroup ptmConfig = PublicTransitMappingConfigGroup.createDefaultConfig();

		new PTMapper(ptmConfig, schedule, network).run();

		NetworkTools.writeNetwork(network, input+"output/addison_network.xml.gz");
		ScheduleTools.writeTransitSchedule(schedule, input+"output/addison_schedule.xml.gz");
	}

	public void analysis() {
		new File(input + "output/").mkdirs();

		MappingAnalysis analysis = new MappingAnalysis(schedule, network, ShapeTools.readShapesFile(input + "addisoncounty-vt-us-gtfs/shapes.txt", coordinateSystem));

		analysis.run();
		analysis.writeAllDistancesCsv(input+"output/DistancesAll.csv");
		analysis.writeQuantileDistancesCsv(input+"output/DistancesQuantile.csv");
		System.out.println(analysis.getQ8585());
		System.out.println(Math.sqrt(analysis.getAverageSquaredLengthRatio()));
	}
}