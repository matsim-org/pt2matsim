package org.matsim.pt2matsim.workbench;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Network;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt2matsim.config.OsmConverterConfigGroup;
import org.matsim.pt2matsim.config.PublicTransitMappingConfigGroup;
import org.matsim.pt2matsim.gtfs.GtfsConverter;
import org.matsim.pt2matsim.gtfs.GtfsFeed;
import org.matsim.pt2matsim.gtfs.GtfsFeedImpl;
import org.matsim.pt2matsim.gtfs.lib.GtfsDefinitions;
import org.matsim.pt2matsim.lib.RouteShape;
import org.matsim.pt2matsim.mapping.PTMapper;
import org.matsim.pt2matsim.mapping.networkRouter.ScheduleRoutersWithShapes;
import org.matsim.pt2matsim.osm.OsmMultimodalNetworkConverter;
import org.matsim.pt2matsim.osm.lib.OsmData;
import org.matsim.pt2matsim.osm.lib.OsmDataImpl;
import org.matsim.pt2matsim.osm.lib.OsmFileReader;
import org.matsim.pt2matsim.plausibility.MappingAnalysis;
import org.matsim.pt2matsim.tools.NetworkTools;
import org.matsim.pt2matsim.tools.ScheduleTools;
import org.matsim.pt2matsim.tools.ShapeTools;
import org.matsim.pt2matsim.tools.debug.ExtractDebugSchedule;
import org.matsim.pt2matsim.tools.debug.ScheduleCleaner;

import java.util.HashSet;
import java.util.Map;

import static org.matsim.pt2matsim.workbench.PTMapperShapesTest.createPTMConfig;

/**
 * @author polettif
 */
public class ZVVTest {


	private String base = "test/zvv/";
	private String osmName = base + "osmData/zurich.osmData";
	private String gtfsFolder = base + "gtfs/";
	private String gtfsShapeFile = gtfsFolder + GtfsDefinitions.Files.SHAPES.fileName;
	private String inputNetworkFile = base + "network/network_unmapped.xml.gz";
	private String fullScheduleFileUM = base + "mts/schedule_unmapped_full.xml.gz";
	private String inputScheduleFile = base + "mts/schedule_unmapped.xml.gz";

	private String coordSys = "EPSG:2056";
	private String outputNetwork1 = base + "output/standard_network.xml.gz";
	private String outputSchedule1 = base + "output/standard_schedule.xml.gz";
	private String outputNetwork2 = base + "output/shapes_network.xml.gz";
	private String outputSchedule2 = base + "output/shapes_schedule.xml.gz";
	private String outputNetwork3 = base + "output/osm_network.xml.gz";
	private String outputSchedule3 = base + "output/osm_schedule.xml.gz";
	private OsmData osmData;

	public static void main(String[] args) throws Exception {
		ZVVTest obj = new ZVVTest();
		obj.convert();
		obj.runMappingStandard();
		obj.runMappingShapes();
		obj.runMappingOsm();
	}

	private void convert() {
		// convert OSM
		OsmConverterConfigGroup osmConfig = OsmConverterConfigGroup.createDefaultConfig();
		osmConfig.setKeepPaths(true);
		osmConfig.setOutputCoordinateSystem(coordSys);

		osmData = new OsmDataImpl();
		new OsmFileReader(osmData).readFile(osmName);
		OsmMultimodalNetworkConverter osmConverter = new OsmMultimodalNetworkConverter(osmData);
		osmConverter.convert(osmConfig);
		NetworkTools.writeNetwork(osmConverter.getNetwork(), inputNetworkFile);

		GtfsFeed gtfsFeed = new GtfsFeedImpl(gtfsFolder);
		GtfsConverter gtfsConverter = new GtfsConverter(gtfsFeed);
		gtfsConverter.convert(GtfsConverter.DAY_WITH_MOST_SERVICES, coordSys);

		ScheduleTools.writeTransitSchedule(gtfsConverter.getSchedule(), fullScheduleFileUM);
		TransitSchedule schedule = ScheduleTools.readTransitSchedule(fullScheduleFileUM);
		for(TransitLine transitLine : new HashSet<>(schedule.getTransitLines().values())) {
			for(TransitRoute transitRoute : new HashSet<>(transitLine.getRoutes().values())) {
				if(!transitRoute.getTransportMode().equals("bus")) {
					transitLine.removeRoute(transitRoute);
				}
			}
		}
		ExtractDebugSchedule.removeRand(schedule, 100);
		ScheduleCleaner.removeNotUsedStopFacilities(schedule);
		ScheduleTools.writeTransitSchedule(schedule, inputScheduleFile);
	}

	/**
	 * 1
	 */
	public void runMappingStandard() {
		TransitSchedule schedule = ScheduleTools.readTransitSchedule(inputScheduleFile);
		Network network = NetworkTools.readNetwork(inputNetworkFile);

		PublicTransitMappingConfigGroup config = createPTMConfig();

		PTMapper ptMapper = new PTMapper(config, schedule, network);
		ptMapper.run();

		NetworkTools.writeNetwork(network, outputNetwork1);
		ScheduleTools.writeTransitSchedule(ptMapper.getSchedule(), outputSchedule1);

		// analysis
		MappingAnalysis analysis = new MappingAnalysis(
				ScheduleTools.readTransitSchedule(outputSchedule1),
				NetworkTools.readNetwork(outputNetwork1),
				ShapeTools.readShapesFile(gtfsShapeFile, coordSys)
		);

		analysis.run();
//		analysis.writeQuantileDistancesCsv(base + "output/DistancesQuantile.csv");
		System.out.println("Q8585: " + analysis.getQ8585());
		System.out.println("Length diff: " + Math.sqrt(analysis.getAverageSquaredLengthRatio()) * 100 + " %");
	}

	/**
	 * 2
	 */
	public void runMappingShapes() {
		TransitSchedule schedule = ScheduleTools.readTransitSchedule(inputScheduleFile);
		Network network = NetworkTools.readNetwork(inputNetworkFile);

		PublicTransitMappingConfigGroup config = createPTMConfig();
		Map<Id<RouteShape>, RouteShape> shapes = ShapeTools.readShapesFile(gtfsShapeFile, coordSys);

		PTMapper ptMapper = new PTMapper(config, schedule, network, new ScheduleRoutersWithShapes(config, schedule, network, shapes));
		ptMapper.run();

		NetworkTools.writeNetwork(network, outputNetwork2);
		ScheduleTools.writeTransitSchedule(ptMapper.getSchedule(), outputSchedule2);

		// analysis
		MappingAnalysis analysis = new MappingAnalysis(
				ScheduleTools.readTransitSchedule(outputSchedule2),
				NetworkTools.readNetwork(outputNetwork2),
				ShapeTools.readShapesFile(gtfsShapeFile, coordSys)
		);

		analysis.run();
//		analysis.writeQuantileDistancesCsv(base + "output/DistancesQuantile.csv");
		System.out.println("Q8585: " + analysis.getQ8585());
		System.out.println("Length diff: " + Math.sqrt(analysis.getAverageSquaredLengthRatio()) * 100 + " %");
	}

	/**
	 * 3
	 */
	public void runMappingOsm() {
		/*
		TransitSchedule schedule = ScheduleTools.readTransitSchedule(inputScheduleFile);
		Network network = NetworkTools.readNetwork(inputNetworkFile);

		PublicTransitMappingConfigGroup config = createPTMConfig();

		PTMapper ptMapper = new PTMapper(config, schedule, network, new ScheduleRoutersOsmAtributes(config, schedule, network, osmData));
		ptMapper.run();

		NetworkTools.writeNetwork(network, outputNetwork3);
		ScheduleTools.writeTransitSchedule(ptMapper.getSchedule(), outputSchedule3);

		// analysis
		MappingAnalysis analysis = new MappingAnalysis(
				ScheduleTools.readTransitSchedule(outputSchedule2),
				NetworkTools.readNetwork(outputNetwork2),
				ShapeTools.readShapesFile(gtfsShapeFile, coordSys)
		);

		analysis.run();
//		analysis.writeQuantileDistancesCsv(base + "output/DistancesQuantile.csv");
		System.out.println("Q8585: " + analysis.getQ8585());
		System.out.println("Length diff: " + Math.sqrt(analysis.getAverageSquaredLengthRatio()) * 100 + " %");
		*/
	}


}

