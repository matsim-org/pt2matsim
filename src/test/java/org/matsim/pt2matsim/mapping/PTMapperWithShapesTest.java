package org.matsim.pt2matsim.mapping;

import org.apache.log4j.Logger;
import org.junit.Before;
import org.junit.Test;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt2matsim.config.PublicTransitMappingConfigGroup;
import org.matsim.pt2matsim.gtfs.GtfsConverter;
import org.matsim.pt2matsim.gtfs.lib.GTFSRoute;
import org.matsim.pt2matsim.gtfs.lib.Shape;
import org.matsim.pt2matsim.gtfs.lib.ShapeSchedule;
import org.matsim.pt2matsim.gtfs.lib.Trip;
import org.matsim.pt2matsim.plausibility.PlausibilityCheck;
import org.matsim.pt2matsim.tools.*;
import org.matsim.vehicles.VehicleUtils;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * @author polettif
 */
public class PTMapperWithShapesTest {

	protected static Logger log = Logger.getLogger(PTMapperWithShapesTest.class);

	private String base = "test/analysis/";
	private String networkName = base + "network/addison.xml.gz";
	private String coordSys = "EPSG:2032";
	private String gtfsFolder = base + "addisoncounty-vt-us-gtfs/";
	private String serviceParam = GtfsConverter.ALL_SERVICE_IDS;

	private GtfsConverter gtfsConverter;
	private Network network;

	@Before
	public void run() throws Exception {
		network = NetworkTools.readNetwork(networkName);

		gtfsConverter = new GtfsConverter(gtfsFolder, coordSys);
		gtfsConverter.convert(serviceParam);

		gtfsConverter.getShapeSchedule().writeShapeScheduleFile(base +"output/ss_file.csv");
		ScheduleTools.writeTransitSchedule(gtfsConverter.getSchedule(), base +"mts/unmapped_schedule.xml.gz");

		// debug shapes
//		ShapeTools.writeShapeFile(Collections.singleton(gtfsConverter.getShapes().get("26")), coordSys, base + "output/gtfsShapeDebug.shp");
	}

	private static PublicTransitMappingConfigGroup createPTMConfig() {
		PublicTransitMappingConfigGroup config = new PublicTransitMappingConfigGroup();
		config.getModesToKeepOnCleanUp().add("car");
		PublicTransitMappingConfigGroup.LinkCandidateCreatorParams lccParamsBus = new PublicTransitMappingConfigGroup.LinkCandidateCreatorParams("bus");
		lccParamsBus.setNetworkModesStr("car,bus");
		lccParamsBus.setMaxNClosestLinks(20);
		lccParamsBus.setMaxLinkCandidateDistance(100);
		lccParamsBus.setLinkDistanceTolerance(1.1);
		config.addParameterSet(lccParamsBus);

		PublicTransitMappingConfigGroup.ModeRoutingAssignment mraBus = new PublicTransitMappingConfigGroup.ModeRoutingAssignment("bus");
		mraBus.setNetworkModesStr("car,bus");
		config.addParameterSet(mraBus);

		config.setNumOfThreads(8);

		return config;
	}

	private void runNormalMapping() {
		PublicTransitMappingConfigGroup config = createPTMConfig();

		TransitSchedule schedule = ScheduleTools.readTransitSchedule(base + "mts/unmapped_schedule.xml.gz");
		PTMapper ptMapper = new PTMapperImpl(config, schedule, network);
		ptMapper.run();

		NetworkTools.writeNetwork(network, base + "output/normal_network.xml.gz");
		ScheduleTools.writeTransitSchedule(ptMapper.getSchedule(), base + "output/normal_schedule.xml.gz");
	}

	private void runMappingWithShapes() {
		PublicTransitMappingConfigGroup config = createPTMConfig();
		ShapeSchedule shapeSchedule = new ShapeSchedule(base + "mts/unmapped_schedule.xml.gz", base + "output/ss_file.csv");

		PTMapper ptMapper = new PTMapperWithShapes(config, shapeSchedule, network);
//		ExtractDebugSchedule.run(shapeSchedule, "TTSB/B_1438", "602798A4122B5456");
		ptMapper.run();

		NetworkTools.writeNetwork(network, base + "output/shapes_network.xml.gz");
		ScheduleTools.writeTransitSchedule(ptMapper.getSchedule(), base + "output/shapes_schedule.xml.gz");
	}

	@Test
	public void mappingAnalysisNormal() {
		runNormalMapping();

		ShapeSchedule shapeSchedule = new ShapeSchedule(base + "output/normal_schedule.xml.gz", base + "output/ss_file.csv");
		MappingAnalysis analysis = new MappingAnalysis(shapeSchedule, NetworkTools.readNetwork(base + "output/normal_network.xml.gz"));

		analysis.run();
		analysis.writeQuantileDistancesCsv(base +"output/Normal_DistancesQuantile.csv");
		System.out.println("Q8585 normal: " + analysis.getQ8585());
	}

	@Test
	public void mappingAnalysisWithShapes() {
		runMappingWithShapes();

		ShapeSchedule shapeSchedule = new ShapeSchedule(base + "output/shapes_schedule.xml.gz", base + "output/ss_file.csv");
		MappingAnalysis analysis = new MappingAnalysis(shapeSchedule, NetworkTools.readNetwork(base + "output/shapes_network.xml.gz"));

		analysis.run();
		analysis.writeQuantileDistancesCsv(base +"/output/Shapes_DistancesQuantile.csv");
		System.out.println("Q8585 with shapes: " + analysis.getQ8585());

		/*
		PlausibilityCheck.run(
				base + "shape/output/shapes_schedule.xml.gz",
				base + "shape/output/shapes_network.xml.gz",
				"EPSG:2032",
				base + "shape/output/check/"
		);
		*/
	}


}