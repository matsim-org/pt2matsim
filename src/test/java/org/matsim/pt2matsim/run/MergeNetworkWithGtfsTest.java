package org.matsim.pt2matsim.run;

import static org.matsim.pt2matsim.gtfs.GtfsConverter.DAY_WITH_MOST_TRIPS;

import org.junit.Test;
import org.matsim.api.core.v01.network.Network;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt2matsim.config.OsmConverterConfigGroup;
import org.matsim.pt2matsim.config.PublicTransitMappingConfigGroup;
import org.matsim.pt2matsim.gtfs.GtfsConverter;
import org.matsim.pt2matsim.gtfs.GtfsFeed;
import org.matsim.pt2matsim.gtfs.GtfsFeedImpl;
import org.matsim.pt2matsim.mapping.PTMapper;
import org.matsim.pt2matsim.osm.OsmMultimodalNetworkConverter;
import org.matsim.pt2matsim.osm.lib.AllowedTagsFilter;
import org.matsim.pt2matsim.osm.lib.Osm;
import org.matsim.pt2matsim.osm.lib.OsmData;
import org.matsim.pt2matsim.osm.lib.OsmDataImpl;
import org.matsim.pt2matsim.osm.lib.OsmFileReader;
import org.matsim.pt2matsim.tools.ScheduleTools;
import org.matsim.vehicles.VehicleUtils;
import org.matsim.vehicles.Vehicles;

public class MergeNetworkWithGtfsTest {

	static final String COORDINATE_SYTEM = "EPSG:31256";

	static final String OSM_FILE = "test/osm/GerasdorfArtificialLanesAndMaxspeed.osm";
	static final String GTFS_FEED = "test/gtfs-feed";
	static final String GTFS_FEED_EMPTY = "test/gtfs-feed-empty";

	@Test
	public void testGtfsFeed() {
		Network network = prepareMatsimnetworkFromOsm(OSM_FILE);
		prepareTransitScheduleFromGtfs(network, GTFS_FEED);
		// expect no exception
	}

	/**
	 * Test that merging the network even works for empty GTFS files. This may be
	 * useful because PTMapper.mapScheduleToNetwork does not only merge the GTFS but
	 * also cleans the network afterwards.
	 * <p>
	 * This way you can reliably create a car (+ bike,..) network with the same
	 * cleaning steps as when creating a car (+ bike,..) + pt network
	 */
	@Test
	public void testEmptyGtfsFeed() {
		Network network = prepareMatsimnetworkFromOsm(OSM_FILE);
		prepareTransitScheduleFromGtfs(network, GTFS_FEED_EMPTY);
		// expect no exception
	}

	public static Network prepareMatsimnetworkFromOsm(String osmFile) {
		OsmConverterConfigGroup config = OsmConverterConfigGroup.createDefaultConfig();

		AllowedTagsFilter filter = new AllowedTagsFilter();
		filter.add(Osm.ElementType.WAY, Osm.Key.HIGHWAY, null);
		filter.add(Osm.ElementType.WAY, Osm.Key.RAILWAY, null);

		OsmData osmData = new OsmDataImpl(filter);
		new OsmFileReader(osmData).readFile(osmFile);

		OsmMultimodalNetworkConverter converter = new OsmMultimodalNetworkConverter(osmData);
		converter.convert(config);

		return converter.getNetwork();
	}

	private static void prepareTransitScheduleFromGtfs(Network network, String gtfsFeed) {
		PublicTransitMappingConfigGroup config = PublicTransitMappingConfigGroup.createDefaultConfig();

		TransitSchedule transitSchedule = prepareTransitSchedule(gtfsFeed);
		Vehicles transitVehicles = VehicleUtils.createVehiclesContainer();
		ScheduleTools.createVehicles(transitSchedule, transitVehicles);

		PTMapper.mapScheduleToNetwork(transitSchedule, network, config);
	}

	private static TransitSchedule prepareTransitSchedule(String gtfsDir) {
		String param = DAY_WITH_MOST_TRIPS;

		// load gtfs files
		GtfsFeed gtfsFeed = new GtfsFeedImpl(gtfsDir);

		// convert to transit schedule
		GtfsConverter converter = new GtfsConverter(gtfsFeed);
		converter.convert(param, COORDINATE_SYTEM);

		return converter.getSchedule();
	}

}
