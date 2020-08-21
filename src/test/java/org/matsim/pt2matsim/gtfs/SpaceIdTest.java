package org.matsim.pt2matsim.gtfs;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;

import org.junit.After;
import org.junit.Test;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitScheduleReader;
import org.matsim.pt.transitSchedule.api.TransitScheduleWriter;
import org.matsim.pt2matsim.config.PublicTransitMappingConfigGroup;
import org.matsim.pt2matsim.mapping.PTMapper;
import org.matsim.pt2matsim.plausibility.PlausibilityCheck;
import org.matsim.pt2matsim.tools.ScheduleTools;
import org.matsim.vehicles.VehicleUtils;
import org.matsim.vehicles.Vehicles;

public class SpaceIdTest {
	@After
	public void cleanup() {
		new File("test_output_schedule.xml").delete();
	}

	/**
	 * GTFS schedules may contain spaces in their stop IDs. As pt2matsim generates
	 * links, those links then also have spaces in their ID. However, in MATSim
	 * network routes are often saved as space-separated sequences of link IDs. If
	 * some links now contian spaces, the route becomes inconsistent as parts of a
	 * link ID are interpreted as IDs on their own. This test fails if spaces are
	 * allowed in stop ids, and is fixed by making sure that pt2matsim only gnerates
	 * link IDs that do not have spaces included.
	 */
	@Test
	public void testFeedWithSpacesInId() {
		GtfsFeed feed = new GtfsFeedImpl("test/space-feed/");
		GtfsConverter covnerter = new GtfsConverter(feed);

		TransitSchedule schedule = ScheduleTools.createSchedule();
		Vehicles vehicles = VehicleUtils.createVehiclesContainer();

		covnerter.convert(GtfsConverter.DAY_WITH_MOST_TRIPS, "EPSG:4326", schedule, vehicles);

		Network network = NetworkUtils.createNetwork();

		Node nodeA = network.getFactory().createNode(Id.createNodeId("A"), new Coord(0.0, 0.0));
		Node nodeB = network.getFactory().createNode(Id.createNodeId("B"), new Coord(1000.0, 1000.0));

		Link linkAB = network.getFactory().createLink(Id.createLinkId("AB"), nodeA, nodeB);
		Link linkBA = network.getFactory().createLink(Id.createLinkId("BA"), nodeB, nodeA);

		linkAB.setAllowedModes(new HashSet<>(Arrays.asList("car", "pt", "bus")));
		linkBA.setAllowedModes(new HashSet<>(Arrays.asList("car", "pt", "bus")));

		network.addNode(nodeA);
		network.addNode(nodeB);
		network.addLink(linkAB);
		network.addLink(linkBA);

		PublicTransitMappingConfigGroup mapperConfig = new PublicTransitMappingConfigGroup();
		PTMapper.mapScheduleToNetwork(schedule, network, mapperConfig);

		new PlausibilityCheck(schedule, network, "EPSG:2154").runCheck();

		new TransitScheduleWriter(schedule).writeFile("test_output_schedule.xml");
		Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
		new TransitScheduleReader(scenario).readFile("test_output_schedule.xml");

		// This fails if spaces in stop ids are not replaced with a placeholder.
		new PlausibilityCheck(scenario.getTransitSchedule(), network, "EPSG:2154").runCheck();
	}
}
