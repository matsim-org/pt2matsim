package org.matsim.pt2matsim.mapping;

import org.apache.log4j.Logger;
import org.junit.Before;
import org.junit.Test;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Network;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt2matsim.config.PublicTransitMappingConfigGroup;
import org.matsim.pt2matsim.gtfs.GtfsConverter;
import org.matsim.pt2matsim.gtfs.GtfsFeed;
import org.matsim.pt2matsim.gtfs.GtfsFeedImpl;
import org.matsim.pt2matsim.lib.RouteShape;
import org.matsim.pt2matsim.mapping.networkRouter.ScheduleRouters;
import org.matsim.pt2matsim.mapping.networkRouter.ScheduleRoutersWithShapes;
import org.matsim.pt2matsim.plausibility.MappingAnalysis;
import org.matsim.pt2matsim.shp.Schedule2ShapeFile;
import org.matsim.pt2matsim.tools.NetworkTools;
import org.matsim.pt2matsim.tools.ScheduleTools;
import org.matsim.pt2matsim.tools.ShapeTools;

import java.util.Map;

/**
 * @author polettif
 */
public class PTMapperImplShapesTest {

	protected static Logger log = Logger.getLogger(PTMapperImplShapesTest.class);

	private String base = "test/analysis/";
	private String networkInput = base + "network/addison.xml.gz";
	private String coordSys = "EPSG:2032";
	private String gtfsFolder = base + "addisoncounty-vt-us-gtfs/";
	private String sampleDay = GtfsConverter.ALL_SERVICE_IDS;

	private GtfsFeed gtfsFeed;

	private Id<TransitLine> debugLineId = Id.create("TTSB/B_1438", TransitLine.class);
	private Id<TransitRoute> debugRouteId = Id.create("602798A4122B5456", TransitRoute.class);
	private String debugDescr = "shapeId:26";

	private String unmappedScheduleFile = base + "mts/unmapped_schedule.xml.gz";

	private String networkOutput1 = base + "output/normal_network.xml.gz";
	private String scheduleOutput1 = base + "output/normal_schedule.xml.gz";
	private String networkOutput2 = base + "output/shapes_network.xml.gz";
	private String scheduleOutput2 = base + "output/shapes_schedule.xml.gz";

	public static PublicTransitMappingConfigGroup createPTMConfig() {
		PublicTransitMappingConfigGroup config = new PublicTransitMappingConfigGroup();
		config.getModesToKeepOnCleanUp().add("car");
		PublicTransitMappingConfigGroup.LinkCandidateCreatorParams lccParamsBus = new PublicTransitMappingConfigGroup.LinkCandidateCreatorParams("bus");
		lccParamsBus.setNetworkModesStr("car,bus");
		lccParamsBus.setMaxNClosestLinks(16);
		lccParamsBus.setMaxLinkCandidateDistance(100);
		lccParamsBus.setLinkDistanceTolerance(1.1);
		config.addParameterSet(lccParamsBus);

		PublicTransitMappingConfigGroup.ModeRoutingAssignment mraBus = new PublicTransitMappingConfigGroup.ModeRoutingAssignment("bus");
		mraBus.setNetworkModesStr("car,bus");
		config.addParameterSet(mraBus);

		config.setNumOfThreads(8);

		return config;
	}

	@Before
	public void convertGtfs() throws Exception {
		gtfsFeed = new GtfsFeedImpl(gtfsFolder);
		GtfsConverter gtfsConverter = new GtfsConverter(gtfsFeed);
		gtfsConverter.convert(sampleDay, coordSys);

		TransitSchedule schedule = gtfsConverter.getSchedule();
		// debug
		// ExtractDebugSchedule.run(schedule, debugLineId.toString(), debugRouteId.toString());
		ScheduleTools.writeTransitSchedule(schedule, unmappedScheduleFile);
	}

	private void runNormalMapping() {
		PublicTransitMappingConfigGroup config = createPTMConfig();

		TransitSchedule schedule = ScheduleTools.readTransitSchedule(unmappedScheduleFile);
		Network network = NetworkTools.readNetwork(networkInput);
		PTMapper ptMapper = new PTMapperImpl(config, schedule, network);
		ptMapper.run();

		NetworkTools.writeNetwork(network, networkOutput1);
		ScheduleTools.writeTransitSchedule(ptMapper.getSchedule(), scheduleOutput1);
	}

	private void runMappingWithShapes() {
		PublicTransitMappingConfigGroup config = createPTMConfig();
		TransitSchedule schedule = ScheduleTools.readTransitSchedule(unmappedScheduleFile);
		Network network = NetworkTools.readNetwork(networkInput);

		ScheduleRouters routers = new ScheduleRoutersWithShapes(config, schedule, network, ShapeTools.readShapesFile(gtfsFolder + "shapes.txt", coordSys));

		PTMapper ptMapper = new PTMapperImpl(config, schedule, network, routers);
		ptMapper.run();

		NetworkTools.writeNetwork(network, networkOutput2);
		ScheduleTools.writeTransitSchedule(ptMapper.getSchedule(), scheduleOutput2);

	}

	@Test
	public void mappingAnalysisNormal() {
		runNormalMapping();

		MappingAnalysis analysis = new MappingAnalysis(
				ScheduleTools.readTransitSchedule(base + "output/normal_schedule.xml.gz"),
				NetworkTools.readNetwork(base + "output/normal_network.xml.gz"),
				ShapeTools.readShapesFile(gtfsFolder + "shapes.txt", coordSys)
		);

		analysis.run();
		analysis.writeQuantileDistancesCsv(base +"output/Normal_DistancesQuantile.csv");
		System.out.println("Q8585 normal: " + analysis.getQ8585());
		System.out.println("Length diff. normal: " + Math.sqrt(analysis.getAverageSquaredLengthRatio())*100 + " %");
	}

	@Test
	public void mappingAnalysisWithShapes() {
		runMappingWithShapes();

		MappingAnalysis analysis = new MappingAnalysis(
				ScheduleTools.readTransitSchedule(scheduleOutput2),
				NetworkTools.readNetwork(networkOutput2),
				ShapeTools.readShapesFile(gtfsFolder + "shapes.txt", coordSys)
		);

		analysis.run();
		analysis.writeQuantileDistancesCsv(base +"/output/Shapes_DistancesQuantile.csv");
		System.out.println("Q8585 with shapes: " + analysis.getQ8585());
		System.out.println("Length diff. shapes: " + Math.sqrt(analysis.getAverageSquaredLengthRatio())*100 + " %");

		/*
		PlausibilityCheck.run(
				base + "shape/output/shapes_schedule.xml.gz",
				base + "shape/output/shapes_network.xml.gz",
				"EPSG:2032",
				base + "shape/output/check/"
		);
		*/
	}

	@Test
	public void writeShapes() {
		Schedule2ShapeFile.run(coordSys, base + "output/shp/", base + "output/shapes_schedule.xml.gz", base + "output/shapes_network.xml.gz");

		Map<Id<RouteShape>, RouteShape> shapes = ShapeTools.readShapesFile(gtfsFolder + "shapes.txt", coordSys);

		Id<RouteShape> shapeId = ScheduleTools.getShapeIdFromDescription(debugDescr);
//		ShapeTools.writeESRIShapeFile(Collections.singletonList(shapes.get(shapeId)), coordSys, base + "output/shp/gtfsShapes.shp");
	}


}