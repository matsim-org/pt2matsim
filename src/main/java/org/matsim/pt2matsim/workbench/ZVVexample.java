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
import org.matsim.pt2matsim.tools.lib.RouteShape;
import org.matsim.pt2matsim.mapping.PTMapper;
import org.matsim.pt2matsim.mapping.networkRouter.ScheduleRouters;
import org.matsim.pt2matsim.mapping.networkRouter.ScheduleRoutersFactory;
import org.matsim.pt2matsim.mapping.networkRouter.ScheduleRoutersGtfsShapes;
import org.matsim.pt2matsim.mapping.networkRouter.ScheduleRoutersOsmAttributes;
import org.matsim.pt2matsim.osm.OsmMultimodalNetworkConverter;
import org.matsim.pt2matsim.osm.lib.*;
import org.matsim.pt2matsim.plausibility.MappingAnalysis;
import org.matsim.pt2matsim.run.gis.Schedule2ShapeFile;
import org.matsim.pt2matsim.tools.NetworkTools;
import org.matsim.pt2matsim.tools.ScheduleTools;
import org.matsim.pt2matsim.tools.ShapeTools;
import org.matsim.pt2matsim.tools.debug.ScheduleCleaner;

import java.util.*;

/**
 * Mapping example for public transit in the zurich area (agency: ZVV).
 * <p>
 * Transit schedule is available on opentransportdata.swiss
 *
 * @author polettif
 * @deprecated input files not in repository
 */
@Deprecated
public class ZVVexample {

	private static String base = "zvv/";
	private static String osmName = base + "osm/zurich.osm";
	private static String gtfsFolder = base + "gtfs/";
	private static String gtfsShapeFile = gtfsFolder + GtfsDefinitions.Files.SHAPES.fileName;
	private static String inputNetworkFile = base + "network/network_unmapped.xml.gz";
	private static String fullScheduleFileUM = base + "mts/schedule_unmapped_full.xml.gz";
	private static String inputScheduleFile = base + "mts/schedule_unmapped.xml.gz";

	private static String coordSys = "EPSG:2056";
	private static String outputNetwork1 = base + "output/standard_network.xml.gz";
	private static String outputSchedule1 = base + "output/standard_schedule.xml.gz";
	private static String outputNetwork2 = base + "output/shapes_network.xml.gz";
	private static String outputSchedule2 = base + "output/shapes_schedule.xml.gz";
	private static String outputNetwork3 = base + "output/osm_network.xml.gz";
	private static String outputSchedule3 = base + "output/osm_schedule.xml.gz";
	private static OsmData osmData;

	public static void main(String[] args) {
//		convertOsm();
//		convertSchedule();
		filterSchedule();
		convertShapes();
		runMappingStandard();
//		runMappingShapes();
//		runMappingOsm();
	}

	private static void convertOsm() {
		// 1. 	convert OSM
		// 1.1. setup config
		Set<String> carSingleton = Collections.singleton("car");
		OsmConverterConfigGroup osmConfig = new OsmConverterConfigGroup();
		osmConfig.addParameterSet(new OsmConverterConfigGroup.OsmWayParams(Osm.Key.HIGHWAY, Osm.Value.MOTORWAY, 2, 120.0 / 3.6, 1.0, 2000, true, carSingleton));
		osmConfig.addParameterSet(new OsmConverterConfigGroup.OsmWayParams(Osm.Key.HIGHWAY, Osm.Value.MOTORWAY, 2, 120.0 / 3.6, 1.0, 2000, true, carSingleton));
		osmConfig.addParameterSet(new OsmConverterConfigGroup.OsmWayParams(Osm.Key.HIGHWAY, Osm.Value.MOTORWAY_LINK, 1, 80.0 / 3.6, 1.0, 1500, true, carSingleton));
		osmConfig.addParameterSet(new OsmConverterConfigGroup.OsmWayParams(Osm.Key.HIGHWAY, Osm.Value.TRUNK, 1, 80.0 / 3.6, 1.0, 2000, false, carSingleton));
		osmConfig.addParameterSet(new OsmConverterConfigGroup.OsmWayParams(Osm.Key.HIGHWAY, Osm.Value.TRUNK_LINK, 1, 50.0 / 3.6, 1.0, 1500, false, carSingleton));
		osmConfig.addParameterSet(new OsmConverterConfigGroup.OsmWayParams(Osm.Key.HIGHWAY, Osm.Value.PRIMARY, 1, 80.0 / 3.6, 1.0, 1500, false, carSingleton));
		osmConfig.addParameterSet(new OsmConverterConfigGroup.OsmWayParams(Osm.Key.HIGHWAY, Osm.Value.PRIMARY_LINK, 1, 60.0 / 3.6, 1.0, 1500, false, carSingleton));
		osmConfig.addParameterSet(new OsmConverterConfigGroup.OsmWayParams(Osm.Key.HIGHWAY, Osm.Value.SECONDARY, 1, 60.0 / 3.6, 1.0, 1000, false, carSingleton));
		osmConfig.addParameterSet(new OsmConverterConfigGroup.OsmWayParams(Osm.Key.HIGHWAY, Osm.Value.TERTIARY, 1, 50.0 / 3.6, 1.0, 600, false, carSingleton));
		osmConfig.addParameterSet(new OsmConverterConfigGroup.OsmWayParams(Osm.Key.HIGHWAY, Osm.Value.UNCLASSIFIED, 1, 50.0 / 3.6, 1.0, 600, false, carSingleton));
		osmConfig.addParameterSet(new OsmConverterConfigGroup.OsmWayParams(Osm.Key.HIGHWAY, Osm.Value.RESIDENTIAL, 1, 30.0 / 3.6, 1.0, 600, false, carSingleton));
		osmConfig.addParameterSet(new OsmConverterConfigGroup.OsmWayParams(Osm.Key.HIGHWAY, Osm.Value.LIVING_STREET, 1, 15.0 / 3.6, 1.0, 300, false, carSingleton));
//		osmConfig.addParameterSet(new OsmConverterConfigGroup.OsmWayParams(Osm.Key.HIGHWAY, Osm.Value.SERVICE, 1, 15.0 / 3.6, 1.0, 200, 	false, carSingleton));

		osmConfig.setKeepPaths(true);
		osmConfig.setOutputCoordinateSystem(coordSys);

		// 1.2 load osm file
		osmData = new OsmDataImpl(createDefaultPTFilter());
		new OsmFileReader(osmData).readFile(osmName);

		// 1.3 initiate and run converter
		OsmMultimodalNetworkConverter osmConverter = new OsmMultimodalNetworkConverter(osmData);
		osmConverter.convert(osmConfig);

		// 1.4 write converted network
		NetworkTools.writeNetwork(osmConverter.getNetwork(), inputNetworkFile);
	}

	public static AllowedTagsFilter createDefaultPTFilter() {
		AllowedTagsFilter filter = new AllowedTagsFilter();
		filter.add(Osm.ElementType.WAY, Osm.Key.HIGHWAY, null);
		filter.add(Osm.ElementType.WAY, Osm.Key.RAILWAY, null);
		filter.addException(Osm.ElementType.WAY, Osm.Key.SERVICE, null);
		filter.add(Osm.ElementType.RELATION, Osm.Key.ROUTE, Osm.Value.BUS);
		filter.add(Osm.ElementType.RELATION, Osm.Key.ROUTE, Osm.Value.TROLLEYBUS);
		filter.add(Osm.ElementType.RELATION, Osm.Key.ROUTE, Osm.Value.RAIL);
		filter.add(Osm.ElementType.RELATION, Osm.Key.ROUTE, Osm.Value.TRAM);
		filter.add(Osm.ElementType.RELATION, Osm.Key.ROUTE, Osm.Value.LIGHT_RAIL);
		filter.add(Osm.ElementType.RELATION, Osm.Key.ROUTE, Osm.Value.FUNICULAR);
		filter.add(Osm.ElementType.RELATION, Osm.Key.ROUTE, Osm.Value.MONORAIL);
		filter.add(Osm.ElementType.RELATION, Osm.Key.ROUTE, Osm.Value.SUBWAY);

		filter.add(Osm.ElementType.RELATION, Osm.Key.ROUTE_MASTER, Osm.Value.BUS);
		filter.add(Osm.ElementType.RELATION, Osm.Key.ROUTE_MASTER, Osm.Value.TROLLEYBUS);
		filter.add(Osm.ElementType.RELATION, Osm.Key.ROUTE_MASTER, Osm.Value.TRAM);
		filter.add(Osm.ElementType.RELATION, Osm.Key.ROUTE_MASTER, Osm.Value.MONORAIL);
		filter.add(Osm.ElementType.RELATION, Osm.Key.ROUTE_MASTER, Osm.Value.SUBWAY);
		filter.add(Osm.ElementType.RELATION, Osm.Key.ROUTE_MASTER, Osm.Value.FERRY);

		return filter;
	}

	public static void convertSchedule() {
		// 2. create schedule
		// 2.1 Load gtfs feed
		GtfsFeed gtfsFeed = new GtfsFeedImpl(gtfsFolder);

		// 2. convert gtfs to a unmapped schedule
		GtfsConverter gtfsConverter = new GtfsConverter(gtfsFeed);
		gtfsConverter.convert("20160303", coordSys);

		// 3. write the transit schedule
		ScheduleTools.writeTransitSchedule(gtfsConverter.getSchedule(), fullScheduleFileUM);
	}

	public static void filterSchedule() {
		TransitSchedule schedule = ScheduleTools.readTransitSchedule(fullScheduleFileUM);

		for(TransitLine transitLine : new HashSet<>(schedule.getTransitLines().values())) {
			for(TransitRoute transitRoute : new HashSet<>(transitLine.getRoutes().values())) {
				if(!transitRoute.getTransportMode().equals("bus")) {
					transitLine.removeRoute(transitRoute);
				}
			}
		}
//		ExtractDebugSchedule.removeRand(schedule, 100);
		ScheduleCleaner.removeNotUsedStopFacilities(schedule);
		filterOneRoutePerLine(schedule);
		removeRouteWithIncorrectShapes(schedule);

		ScheduleTools.writeTransitSchedule(schedule, inputScheduleFile);
	}

	public static void convertShapes() {
		TransitSchedule schedule = ScheduleTools.readTransitSchedule(inputScheduleFile);

		Map<Id<RouteShape>, RouteShape> shapes = ShapeTools.readShapesFile(gtfsShapeFile, coordSys);
		Map<Id<RouteShape>, RouteShape> shapesToConvert = new HashMap<>();


		for(TransitLine transitLine : schedule.getTransitLines().values()) {
			for(TransitRoute transitRoute : transitLine.getRoutes().values()) {
				Id<RouteShape> id = ScheduleTools.getShapeId(transitRoute);
				shapesToConvert.put(id, shapes.get(id));
			}
		}


		ShapeTools.writeESRIShapeFile(shapesToConvert.values(), coordSys, base + "output/shp/gtfs.shp");
	}

	/**
	 * Runs a standard mapping
	 */
	public static double runMappingStandard() {
		// Load schedule and network
		TransitSchedule schedule = ScheduleTools.readTransitSchedule(inputScheduleFile);
		Network network = NetworkTools.readNetwork(inputNetworkFile);

		// create PTM config
		PublicTransitMappingConfigGroup config = PublicTransitMappingConfigGroup.createDefaultConfig();

		// run PTMapper
		PTMapper ptMapper = new PTMapper(schedule, network);
		ptMapper.run(config);

		//
		NetworkTools.writeNetwork(network, outputNetwork1);
		ScheduleTools.writeTransitSchedule(schedule, outputSchedule1);

		Schedule2ShapeFile.run(coordSys, base + "output/shp/", schedule, network);

		// analyse result
		return runAnalysis(outputSchedule1, outputNetwork1);
	}

	/**
	 * Maps a schedule with gtfs shape information to the network
	 */
	public static void runMappingShapes() {
		System.out.println("===================");
		System.out.println("Run mapping: SHAPES");
		System.out.println("===================");

		TransitSchedule schedule = ScheduleTools.readTransitSchedule(inputScheduleFile);
		Network network = NetworkTools.readNetwork(inputNetworkFile);

		PublicTransitMappingConfigGroup config = PublicTransitMappingConfigGroup.createDefaultConfig();
		Map<Id<RouteShape>, RouteShape> shapes = ShapeTools.readShapesFile(gtfsShapeFile, coordSys);

		ScheduleRoutersFactory routerFactory = new ScheduleRoutersGtfsShapes.Factory(schedule, network, shapes, config.getTransportModeAssignment(), config.getTravelCostType(), 50, 250);

		PTMapper ptMapper = new PTMapper(schedule, network);
		ptMapper.run(config, null, routerFactory);

		NetworkTools.writeNetwork(network, outputNetwork2);
		ScheduleTools.writeTransitSchedule(schedule, outputSchedule2);

		// analysis
		runAnalysis(outputSchedule2, outputNetwork2);
	}

	/**
	 * Analyses the mapping result
	 */
	private static double runAnalysis(String scheduleFile, String networkFile) {
		MappingAnalysis analysis = new MappingAnalysis(
				ScheduleTools.readTransitSchedule(scheduleFile),
				NetworkTools.readNetwork(networkFile),
				ShapeTools.readShapesFile(gtfsShapeFile, coordSys)
		);
		analysis.run();
		analysis.writeQuantileDistancesCsv(base + "output/analysis/quantiles.csv");

		return analysis.getQ8585();
	}

	/**
	 * Maps a schedule using osm pt information of the network
	 */
	public static void runMappingOsm() {
		TransitSchedule schedule = ScheduleTools.readTransitSchedule(inputScheduleFile);
		Network network = NetworkTools.readNetwork(inputNetworkFile);

		PublicTransitMappingConfigGroup config = PublicTransitMappingConfigGroup.createDefaultConfig();

		// Initiate Router that uses osm data
		ScheduleRoutersFactory routerFactory = new ScheduleRoutersOsmAttributes.Factory(schedule, network, config.getTransportModeAssignment(), PublicTransitMappingConfigGroup.TravelCostType.linkLength, 0.5);

		PTMapper ptMapper = new PTMapper(schedule, network);
		ptMapper.run(config, null, routerFactory);

		NetworkTools.writeNetwork(network, outputNetwork3);
		ScheduleTools.writeTransitSchedule(schedule, outputSchedule3);

		runAnalysis(outputSchedule3, outputNetwork3);
	}

	private static void filterOneRoutePerLine(TransitSchedule schedule) {
		for(TransitLine transitLine : new HashSet<>(schedule.getTransitLines().values())) {
			int maxDep = 0;
			Id<TransitRoute> max = null;
			for(TransitRoute transitRoute : transitLine.getRoutes().values()) {
				if(transitRoute.getDepartures().size() > maxDep) {
					maxDep = transitRoute.getDepartures().size();
					max = transitRoute.getId();
				}
			}

			for(TransitRoute t : new HashSet<>(transitLine.getRoutes().values())) {
				if(!t.getId().equals(max)) {
					transitLine.removeRoute(t);
				}
			}
		}
	}

	private static void removeRouteWithIncorrectShapes(TransitSchedule schedule) {
		Set<String> routesToRemove = new HashSet<>();
		routesToRemove.add("80.T0.31-650-P-j16-1.6.H"); // straight shape line
		routesToRemove.add("852.T0.21-105-P-j16-1.4.R"); // straight shape line
		routesToRemove.add("101.T0.21-611-P-j16-1.3.H"); // line too short
		routesToRemove.add("79.T0.21-611-P-j16-1.10.R"); // line too short

		for(TransitLine transitLine : schedule.getTransitLines().values()) {
			for(TransitRoute transitRoute : new HashSet<>(transitLine.getRoutes().values())) {
				if(routesToRemove.contains(transitRoute.getId().toString())) {
					transitLine.removeRoute(transitRoute);
					System.out.println(transitRoute + " removed!");
				}
			}
		}
	}

}

