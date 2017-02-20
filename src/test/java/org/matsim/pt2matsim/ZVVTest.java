package org.matsim.pt2matsim;

import org.junit.Before;
import org.junit.Test;
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
import org.matsim.pt2matsim.mapping.PTMapperImpl;
import org.matsim.pt2matsim.mapping.networkRouter.ScheduleRoutersWithShapes;
import org.matsim.pt2matsim.osm.OsmMultimodalNetworkConverterAttr;
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

import static org.matsim.pt2matsim.mapping.PTMapperImplShapesTest.createPTMConfig;

/**
 * @author polettif
 */
public class ZVVTest {


	private String base = "test/zvv/";
	private String osmName = base + "osm/zurich.osm";
	private String gtfsFolder = base + "gtfs/";
	private String gtfsShapeFile = gtfsFolder + GtfsDefinitions.Files.SHAPES.fileName;
	private String inputNetworkFile = base + "network/network_unmapped.xml.gz";
	private String fullScheduleFileUM = base + "mts/schedule_unmapped_full.xml.gz";
	private String inputScheduleFile = base + "mts/schedule_unmapped.xml.gz";

	private String coordSys = "EPSG:2056";
	private String outputNetwork1 = base + "output/shapes_network.xml.gz";
	private String outputSchedule1 = base + "output/shapes_schedule.xml.gz";


	@Before
	public void prepare() {
		convert();
	}

	private void convert() {
		// convert OSM
		OsmConverterConfigGroup osmConfig = OsmConverterConfigGroup.createDefaultConfig();
		osmConfig.setKeepPaths(true);
		osmConfig.setOutputCoordinateSystem(coordSys);

		OsmData osm = new OsmDataImpl();
		new OsmFileReader(osm).readFile(osmName);
		OsmMultimodalNetworkConverterAttr osmConverter = new OsmMultimodalNetworkConverterAttr(osm);
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


	private void runMapping() {
		TransitSchedule schedule = ScheduleTools.readTransitSchedule(inputScheduleFile);
		Network network = NetworkTools.readNetwork(inputNetworkFile);

		PublicTransitMappingConfigGroup config = createPTMConfig();
		Map<Id<RouteShape>, RouteShape> shapes = ShapeTools.readShapesFile(gtfsShapeFile, coordSys);

		PTMapper ptMapper = new PTMapperImpl(config, schedule, network, new ScheduleRoutersWithShapes(config, schedule, network, shapes));
		ptMapper.run();

		NetworkTools.writeNetwork(network, outputNetwork1);
		ScheduleTools.writeTransitSchedule(ptMapper.getSchedule(), outputSchedule1);
	}

	@Test
	public void runMappingAnalysisShapes() {
		runMapping();

		MappingAnalysis analysis = new MappingAnalysis(
				ScheduleTools.readTransitSchedule(outputSchedule1),
				NetworkTools.readNetwork(outputNetwork1),
				ShapeTools.readShapesFile(gtfsShapeFile, coordSys)
		);

		analysis.run();
		analysis.writeQuantileDistancesCsv(base + "output/DistancesQuantile.csv");
		System.out.println("Q8585: " + analysis.getQ8585());
		System.out.println("Length diff: " + Math.sqrt(analysis.getAverageSquaredLengthRatio()) * 100 + " %");
	}

}

