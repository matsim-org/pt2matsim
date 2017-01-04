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

import org.junit.Before;
import org.junit.Test;
import org.matsim.api.core.v01.network.Network;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt2matsim.config.PublicTransitMappingConfigGroup;
import org.matsim.pt2matsim.gtfs.GtfsConverter;
import org.matsim.pt2matsim.gtfs.GtfsFeed;
import org.matsim.pt2matsim.mapping.PTMapperImpl;
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
	private GtfsFeed gtfsConverter;
	private String coordinateSystem;

	private String input = "test/analysis/";


	@Before
	public void prepare() throws IOException {

		network = NetworkTools.readNetwork(input + "network/addison.xml.gz");

		coordinateSystem = "EPSG:2032";

		// convert schedule
		schedule = ScheduleTools.createSchedule();
		vehicles = VehicleUtils.createVehiclesContainer();
		gtfsConverter = new GtfsConverter(input + "addisoncounty-vt-us-gtfs/", coordinateSystem);
		gtfsConverter.convert("all", schedule, vehicles);

//		ExtractDebugSchedule.run(schedule, "SBSB_1437", "59468A1158B4286");

		ScheduleTools.writeTransitSchedule(gtfsConverter.getSchedule(), input + "mts/schedule_unmapped.xml.gz");
		gtfsConverter.getShapedTransitSchedule().getTransitRouteShapeReference().writeToFile(input + "mts/route_shape_ref.csv");

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

	@Before
	public void runMapping() {
		PublicTransitMappingConfigGroup ptmConfig = PublicTransitMappingConfigGroup.createDefaultConfig();

		new PTMapperImpl(ptmConfig, schedule, network).run();

		NetworkTools.writeNetwork(network, input+"output/addison_network.xml.gz");
		ScheduleTools.writeTransitSchedule(schedule, input+"output/addison_schedule.xml.gz");
	}

	@Test
	public void analysis() {
		new File(input + "output/").mkdirs();

		MappingAnalysis analysis = new MappingAnalysis(schedule, network, input + "mts/route_shape_ref.csv", input + "addisoncounty-vt-us-gtfs/shapes.txt", coordinateSystem);

		analysis.run();
		analysis.writeAllDistancesCsv(input+"output/DistancesAll.csv");
		analysis.writeQuantileDistancesCsv(input+"output/DistancesQuantile.csv");
		System.out.println(analysis.getQ8585());
		System.out.println(Math.sqrt(analysis.getAverageSquaredLengthRatio()));
	}
}