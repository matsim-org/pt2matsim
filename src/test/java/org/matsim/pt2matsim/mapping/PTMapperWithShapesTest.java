package org.matsim.pt2matsim.mapping;

import org.apache.log4j.Logger;
import org.junit.Before;
import org.junit.Test;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt2matsim.config.PublicTransitMappingConfigGroup;
import org.matsim.pt2matsim.gtfs.GtfsConverter;
import org.matsim.pt2matsim.gtfs.lib.ShapeSchedule;
import org.matsim.pt2matsim.tools.MappingAnalysis;
import org.matsim.pt2matsim.tools.NetworkTools;
import org.matsim.pt2matsim.tools.ScheduleTools;
import org.matsim.vehicles.VehicleUtils;

/**
 * @author polettif
 */
public class PTMapperWithShapesTest {

	protected static Logger log = Logger.getLogger(PTMapperWithShapesTest.class);


	private GtfsConverter gtfsConverter;
	private Network network;
	private String base = "test/analysis/";

	@Before
	public void run() throws Exception {
		network = NetworkTools.readNetwork(base + "network/addison.xml.gz");

		gtfsConverter = new GtfsConverter(
				ScheduleTools.createSchedule(),
				VehicleUtils.createVehiclesContainer(),
				TransformationFactory.getCoordinateTransformation("WGS84", "EPSG:2032"));

		gtfsConverter.run(base + "addisoncounty-vt-us-gtfs/", "all");
		gtfsConverter.getShapeSchedule().writeShapeScheduleFile(base +"shape/ss_file.csv");
		ScheduleTools.writeTransitSchedule(gtfsConverter.getSchedule(), base +"mts/unmapped_schedule.xml.gz");

//		runNormalMapping();
		runMappingWithShapes();
	}

	private void runNormalMapping() {
		PublicTransitMappingConfigGroup config = PublicTransitMappingConfigGroup.createDefaultConfig();
		config.setNumOfThreads(6);

		TransitSchedule schedule = ScheduleTools.readTransitSchedule(base + "mts/unmapped_schedule.xml.gz");
		PTMapper ptMapper = new PTMapperImpl(config, schedule, network);
		ptMapper.run();

		NetworkTools.writeNetwork(network, base + "shape/output/normal_network.xml.gz");
		ScheduleTools.writeTransitSchedule(ptMapper.getSchedule(), base + "shape/output/normal_schedule.xml.gz");
	}


	private void runMappingWithShapes() {
		PublicTransitMappingConfigGroup config = PublicTransitMappingConfigGroup.createDefaultConfig();
		config.setNumOfThreads(12);

		ShapeSchedule shapeSchedule = new ShapeSchedule(base + "mts/unmapped_schedule.xml.gz", base + "shape/ss_file.csv");
		PTMapper ptMapper = new PTMapperWithShapes(config, shapeSchedule, network);
		ptMapper.run();

		NetworkTools.writeNetwork(network, base + "shape/output/shapes_network.xml.gz");
		ScheduleTools.writeTransitSchedule(ptMapper.getSchedule(), base + "shape/output/shapes_schedule.xml.gz");
	}

	@Test
	public void mappingAnalysisWithShapes() {
		ShapeSchedule shapeSchedule = new ShapeSchedule(base + "shape/output/shapes_schedule.xml.gz", base + "shape/ss_file.csv");
		MappingAnalysis analysis = new MappingAnalysis(shapeSchedule, NetworkTools.readNetwork(base + "shape/output/shapes_network.xml.gz"));

		analysis.run();
		analysis.writeQuantileDistancesCsv(base +"shape/output/Shapes_DistancesQuantile.csv");
		System.out.println("with shapes: " + analysis.getQ8585());
	}

	@Test
	public void mappingAnalysisNormal() {
		ShapeSchedule shapeSchedule = new ShapeSchedule(base + "shape/output/normal_schedule.xml.gz", base + "shape/ss_file.csv");
		MappingAnalysis analysis = new MappingAnalysis(shapeSchedule, NetworkTools.readNetwork(base + "shape/output/normal_network.xml.gz"));

		analysis.run();
		analysis.writeQuantileDistancesCsv(base +"shape/output/Normal_DistancesQuantile.csv");
		System.out.println("normal: " + analysis.getQ8585());
	}
}