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
import org.matsim.pt2matsim.tools.lib.RouteShape;
import org.matsim.pt2matsim.mapping.PTMapper;
import org.matsim.pt2matsim.osm.lib.Osm;
import org.matsim.pt2matsim.plausibility.MappingAnalysis;
import org.matsim.pt2matsim.run.CheckMappedSchedulePlausibility;
import org.matsim.pt2matsim.run.Gtfs2TransitSchedule;
import org.matsim.pt2matsim.run.Osm2MultimodalNetwork;
import org.matsim.pt2matsim.run.gis.Schedule2ShapeFile;
import org.matsim.pt2matsim.tools.NetworkTools;
import org.matsim.pt2matsim.tools.ScheduleTools;
import org.matsim.pt2matsim.tools.ShapeTools;
import org.matsim.pt2matsim.tools.debug.ExtractDebugSchedule;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * Example case of Asheville, North Carolina, USA.
 *
 * @author polettif
 */
@Deprecated
public class Asheville {

	public static final String EPSG = "EPSG:3631";
	public static final String inputNetworkFile = "network/asheville.xml.gz";
	public static final String inputScheduleFile = "schedule/schedule_unmapped.xml.gz";
	public static final String outputNetworkFile = "output/asheville_network.xml.gz";
	public static final String outputScheduleFile = "output/asheville_schedule.xml.gz";
	public static final String outputPlausibility = "output/plausibility/";

	public static void main(String[] args) {
		convertOsm();
		Gtfs2TransitSchedule.run("gtfs/", GtfsConverter.DAY_WITH_MOST_SERVICES, EPSG, inputScheduleFile, "schedule/vhcls.xml.gz");
		runMapping();
		analysis();
		CheckMappedSchedulePlausibility.run(outputScheduleFile, outputNetworkFile, EPSG, outputPlausibility);
	}


	public static void runMapping() {
		PublicTransitMappingConfigGroup config = PublicTransitMappingConfigGroup.createDefaultConfig();
		config.setNumOfThreads(6);

		TransitSchedule schedule = ScheduleTools.readTransitSchedule(inputScheduleFile);
		Network network = NetworkTools.readNetwork(inputNetworkFile);

		PTMapper.mapScheduleToNetwork(schedule, network, config);

		ScheduleTools.writeTransitSchedule(schedule, outputScheduleFile);
		NetworkTools.writeNetwork(network, outputNetworkFile);

		Schedule2ShapeFile.run(EPSG, "output/shp/", schedule, network);
	}

	private static void analysis() {
		TransitSchedule schedule = ScheduleTools.readTransitSchedule(outputScheduleFile);
		Network network = NetworkTools.readNetwork(outputNetworkFile);
		Map<Id<RouteShape>, RouteShape> shapes = ShapeTools.readShapesFile("gtfs/shapes.txt", EPSG);
//		ShapeTools.writeESRIShapeFile(shapes.values(), EPSG, "output/shp/gtfs.shp");

		MappingAnalysis analysis = new MappingAnalysis(schedule, network, shapes);
		analysis.run();
		analysis.writeQuantileDistancesCsv("output/analysis/quantiles.csv");

		// write worst mapped route to file
		String debugLineId = "S3_1137";
		String debugRouteId = "605587A5072B5817";
		ExtractDebugSchedule.writeRouteAndShapeToShapefile(schedule, network, shapes, "output/debug/", debugLineId, debugRouteId, EPSG);
	}

	public static void convertOsm() {
		Set<String> carSingleton = Collections.singleton("car");

		OsmConverterConfigGroup config = new OsmConverterConfigGroup();
		config.addParameterSet(new OsmConverterConfigGroup.OsmWayParams(Osm.Key.HIGHWAY, Osm.Value.MOTORWAY, 2, 120.0 / 3.6, 1.0, 2000, true, carSingleton));
		config.addParameterSet(new OsmConverterConfigGroup.OsmWayParams(Osm.Key.HIGHWAY, Osm.Value.MOTORWAY, 2, 120.0 / 3.6, 1.0, 2000, true, carSingleton));
		config.addParameterSet(new OsmConverterConfigGroup.OsmWayParams(Osm.Key.HIGHWAY, Osm.Value.MOTORWAY_LINK, 1, 80.0 / 3.6, 1.0, 1500, true, carSingleton));
		config.addParameterSet(new OsmConverterConfigGroup.OsmWayParams(Osm.Key.HIGHWAY, Osm.Value.TRUNK, 1, 80.0 / 3.6, 1.0, 2000, false, carSingleton));
		config.addParameterSet(new OsmConverterConfigGroup.OsmWayParams(Osm.Key.HIGHWAY, Osm.Value.TRUNK_LINK, 1, 50.0 / 3.6, 1.0, 1500, false, carSingleton));
		config.addParameterSet(new OsmConverterConfigGroup.OsmWayParams(Osm.Key.HIGHWAY, Osm.Value.PRIMARY, 1, 80.0 / 3.6, 1.0, 1500, false, carSingleton));
		config.addParameterSet(new OsmConverterConfigGroup.OsmWayParams(Osm.Key.HIGHWAY, Osm.Value.PRIMARY_LINK, 1, 60.0 / 3.6, 1.0, 1500, false, carSingleton));
		config.addParameterSet(new OsmConverterConfigGroup.OsmWayParams(Osm.Key.HIGHWAY, Osm.Value.SECONDARY, 1, 60.0 / 3.6, 1.0, 1000, false, carSingleton));
		config.addParameterSet(new OsmConverterConfigGroup.OsmWayParams(Osm.Key.HIGHWAY, Osm.Value.TERTIARY, 1, 50.0 / 3.6, 1.0, 600, false, carSingleton));
		config.addParameterSet(new OsmConverterConfigGroup.OsmWayParams(Osm.Key.HIGHWAY, Osm.Value.UNCLASSIFIED, 1, 50.0 / 3.6, 1.0, 600, false, carSingleton));
		config.addParameterSet(new OsmConverterConfigGroup.OsmWayParams(Osm.Key.HIGHWAY, Osm.Value.RESIDENTIAL, 1, 30.0 / 3.6, 1.0, 600, false, carSingleton));
		config.addParameterSet(new OsmConverterConfigGroup.OsmWayParams(Osm.Key.HIGHWAY, Osm.Value.LIVING_STREET, 1, 15.0 / 3.6, 1.0, 300, false, carSingleton));
		config.addParameterSet(new OsmConverterConfigGroup.OsmWayParams(Osm.Key.HIGHWAY, Osm.Value.SERVICE, 1, 15.0 / 3.6, 1.0, 200, 	false, carSingleton));

		config.setKeepPaths(true);
		config.setOsmFile("network/asheville.osm");
		config.setOutputCoordinateSystem(EPSG);
		config.setOutputNetworkFile(inputNetworkFile);

		Osm2MultimodalNetwork.run(config);
	}

}
