package org.matsim.pt2matsim.workbench;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Network;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt2matsim.config.PublicTransitMappingConfigGroup;
import org.matsim.pt2matsim.gtfs.GtfsConverter;
import org.matsim.pt2matsim.gtfs.GtfsFeed;
import org.matsim.pt2matsim.gtfs.GtfsFeedImpl;
import org.matsim.pt2matsim.tools.lib.RouteShape;
import org.matsim.pt2matsim.mapping.PTMapper;
import org.matsim.pt2matsim.mapping.networkRouter.ScheduleRouters;
import org.matsim.pt2matsim.mapping.networkRouter.ScheduleRoutersGtfsShapes;
import org.matsim.pt2matsim.plausibility.MappingAnalysis;
import org.matsim.pt2matsim.run.shp.Schedule2ShapeFile;
import org.matsim.pt2matsim.tools.NetworkTools;
import org.matsim.pt2matsim.tools.ScheduleTools;
import org.matsim.pt2matsim.tools.ShapeTools;

import java.util.Map;

/**
 * @author polettif
 */
public class PTMapperShapesExample {

	private static String base = "example/";
	private static String networkInput = base + "network/addison.xml.gz";
	private static String coordSys = "EPSG:2032";
	private static String gtfsFolder = base + "addisoncounty-vt-us-gtfs/";
	private static String sampleDay = GtfsConverter.ALL_SERVICE_IDS;

	private static GtfsFeed gtfsFeed;

	private static String unmappedScheduleFile = base + "mts/unmapped_schedule.xml.gz";
	private static String networkOutput1 = base + "output/normal_network.xml.gz";
	private static String scheduleOutput1 = base + "output/normal_schedule.xml.gz";
	private static String networkOutput2 = base + "output/shapes_network.xml.gz";
	private static String scheduleOutput2 = base + "output/shapes_schedule.xml.gz";

	public static void main(String[] args) {
		convertGtfs();
		mappingAnalysisNormal();
		mappingAnalysisWithShapes();
		writeShapes();
	}

	public static PublicTransitMappingConfigGroup createTestPTMConfig() {
		PublicTransitMappingConfigGroup config = new PublicTransitMappingConfigGroup();
		config.getModesToKeepOnCleanUp().add("car");

		config.setNLinkThreshold(16);
		config.setMaxLinkCandidateDistance(100);
		config.setCandidateDistanceMultiplier(1.1);

		PublicTransitMappingConfigGroup.TransportModeAssignment mraBus = new PublicTransitMappingConfigGroup.TransportModeAssignment("bus");
		mraBus.setNetworkModesStr("car,bus");
		config.addParameterSet(mraBus);

		config.setNumOfThreads(8);

		return config;
	}

	public static void convertGtfs() {
		gtfsFeed = new GtfsFeedImpl(gtfsFolder);
		GtfsConverter gtfsConverter = new GtfsConverter(gtfsFeed);
		gtfsConverter.convert(sampleDay, coordSys);
		ScheduleTools.writeTransitSchedule(gtfsConverter.getSchedule(), unmappedScheduleFile);
	}

	private static void runNormalMapping() {
		PublicTransitMappingConfigGroup config = createTestPTMConfig();

		TransitSchedule schedule = ScheduleTools.readTransitSchedule(unmappedScheduleFile);
		Network network = NetworkTools.readNetwork(networkInput);
		PTMapper ptMapper = new PTMapper(schedule, network);
		ptMapper.run(config);

		NetworkTools.writeNetwork(network, networkOutput1);
		ScheduleTools.writeTransitSchedule(ptMapper.getSchedule(), scheduleOutput1);
	}

	private static void runMappingWithShapes() {
		PublicTransitMappingConfigGroup config = createTestPTMConfig();
		TransitSchedule schedule = ScheduleTools.readTransitSchedule(unmappedScheduleFile);
		Network network = NetworkTools.readNetwork(networkInput);

		ScheduleRouters routers = new ScheduleRoutersGtfsShapes(schedule, network,
				ShapeTools.readShapesFile(gtfsFolder + "shapes.txt", coordSys), config.getTransportModeAssignment(), config.getTravelCostType(),
				50, 200);

		PTMapper ptMapper = new PTMapper(schedule, network);
		ptMapper.run(config, routers);

		NetworkTools.writeNetwork(network, networkOutput2);
		ScheduleTools.writeTransitSchedule(ptMapper.getSchedule(), scheduleOutput2);
	}

	public static void mappingAnalysisNormal() {
		runNormalMapping();

		MappingAnalysis analysis = new MappingAnalysis(
				ScheduleTools.readTransitSchedule(base + "output/normal_schedule.xml.gz"),
				NetworkTools.readNetwork(base + "output/normal_network.xml.gz"),
				ShapeTools.readShapesFile(gtfsFolder + "shapes.txt", coordSys)
		);

		analysis.run();
		analysis.writeQuantileDistancesCsv(base + "output/Normal_DistancesQuantile.csv");
	}

	public static void mappingAnalysisWithShapes() {
		runMappingWithShapes();

		MappingAnalysis analysis = new MappingAnalysis(
				ScheduleTools.readTransitSchedule(scheduleOutput2),
				NetworkTools.readNetwork(networkOutput2),
				ShapeTools.readShapesFile(gtfsFolder + "shapes.txt", coordSys)
		);

		analysis.run();
		analysis.writeQuantileDistancesCsv(base + "/output/Shapes_DistancesQuantile.csv");
	}

	public static void writeShapes() {
		Schedule2ShapeFile.run(coordSys, base + "output/shp/", base + "output/shapes_schedule.xml.gz", base + "output/shapes_network.xml.gz");

		Map<Id<RouteShape>, RouteShape> shapes = ShapeTools.readShapesFile(gtfsFolder + "shapes.txt", coordSys);

//		Id<RouteShape> shapeId = Id.create("ID", RouteShape.class);
//		ShapeTools.writeESRIShapeFile(Collections.singletonList(shapes.get(shapeId)), coordSys, base + "output/shp/gtfsShapes.shp");
	}


}