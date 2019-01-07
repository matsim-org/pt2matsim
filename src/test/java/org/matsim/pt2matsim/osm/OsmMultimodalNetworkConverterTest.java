package org.matsim.pt2matsim.osm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.BeforeClass;
import org.junit.Test;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.pt2matsim.config.OsmConverterConfigGroup;
import org.matsim.pt2matsim.osm.lib.OsmData;
import org.matsim.pt2matsim.osm.lib.OsmDataImpl;
import org.matsim.pt2matsim.osm.lib.OsmFileReader;

/**
 * @author polettif
 * @author mstraub - Austrian Institute of Technology
 */
public class OsmMultimodalNetworkConverterTest {
	/** MATSim links created from the Gerasdorf test file */
	private static Map<Long, Set<Link>> osmid2link;
	private static final double DELTA = 0.001;
	
	@BeforeClass
	public static void convertGerasdorfArtificialLanesAndMaxspeed() {
		// setup config
		OsmConverterConfigGroup osmConfig = OsmConverterConfigGroup.createDefaultConfig();
		osmConfig.setOutputCoordinateSystem("EPSG:31256");
		osmConfig.setOsmFile("test/osm/GerasdorfArtificialLanesAndMaxspeed.osm");
		osmConfig.setOutputNetworkFile("test/osm/GerasdorfArtificialLanesAndMaxspeed.xml.gz");
		osmConfig.setMaxLinkLength(1000);

		// read OSM file
		OsmData osm = new OsmDataImpl();
		new OsmFileReader(osm).readFile(osmConfig.getOsmFile());

		// convert
		OsmMultimodalNetworkConverter converter = new OsmMultimodalNetworkConverter(osm);
		converter.convert(osmConfig);

		Network network = converter.getNetwork();
		
		// write file
		//NetworkTools.writeNetwork(network, osmConfig.getOutputNetworkFile());
		
		osmid2link = collectLinkMap(network);
	}
	
	private static Map<Long, Set<Link>> collectLinkMap(Network network) {
		HashMap<Long, Set<Link>> osmid2link = new HashMap<>();
		for (Link l : network.getLinks().values()) {
			long key = (long) l.getAttributes().getAttribute("osm:way:id");
			if (!osmid2link.containsKey(key))
				osmid2link.put(key, new HashSet<>());
			osmid2link.get(key).add(l);
		}
		return osmid2link;
	}
	
	private static Set<Link> getLinksTowardsNode(Set<Link> links, long osmNodeId) {
		return links.stream()
				.filter(l -> l.getToNode().getId().toString().equals("" + osmNodeId))
				.collect(Collectors.toSet());
	}
	
	@Test
	public void testDefaultResidential() {
		Set<Link> links = osmid2link.get(7994891L);
		assertEquals("bidirectional", 2, links.size());
		assertLanes(links, 1);
		assertMaxspeed("taken from OsmConverterConfigGroup.createDefaultConfig", links, 15);
	}
	
	@Test
	public void testDefaultPrimary() {
		Set<Link> links = osmid2link.get(7994890L);
		assertEquals("bidirectional", 2, links.size()); 
		assertLanes("taken from OsmConverterConfigGroup.createDefaultConfig", links, 1);
		assertMaxspeed("taken from OsmConverterConfigGroup.createDefaultConfig", links, 80);
	}
	
	@Test
	public void testPrimaryWithLanesAndMaxspeed() {
		Set<Link> links = osmid2link.get(7994889L);
		assertEquals("bidirectional", 2, links.size());
		assertLanes(links, 3);
		assertMaxspeed(links, 70);
	}

	@Test
	public void testPrimaryWithOddLanesAndMaxspeed() {
		Set<Link> links = osmid2link.get(7994888L);
		assertEquals("bidirectional", 2, links.size());
		assertLanes(links, 3.5);
		assertMaxspeed(links, 70);
	}

	@Test
	public void testPrimaryWithForwardAndBackwardLanesAndMaxspeed() {
		Set<Link> links = osmid2link.get(7994887L);
		assertEquals("bidirectional", 2, links.size());

		Set<Link> linksToNorth = getLinksTowardsNode(links, 59836731L);
		assertLanes(linksToNorth, 3);
		assertMaxspeed(linksToNorth, 70);

		Set<Link> linksToSouth = getLinksTowardsNode(links, 59836730L);
		assertLanes(linksToSouth, 4);
		assertMaxspeed(linksToSouth, 100);
	}
	
	@Test
	public void testPrimaryWithForwardAndBackwardSpecialLanesAndMaxspeed() {
		Set<Link> links = osmid2link.get(7994886L);
		assertEquals("bidirectional", 2, links.size());

		Set<Link> linksToNorth = getLinksTowardsNode(links, 57443579L);
		assertLanes("4 minus one bus lane", linksToNorth, 3);
		assertMaxspeed(linksToNorth, 70);

		Set<Link> linksToSouth = getLinksTowardsNode(links, 59836729L);
		assertLanes("5 minus one psv lane", linksToSouth, 4);
		assertMaxspeed(linksToSouth, 100);
	}

	@Test
	public void testPrimaryWithSpecialLanes() {
		Set<Link> links = osmid2link.get(7994912L);
		assertEquals("bidirectional", 2, links.size());
		assertLanes("4 per direction minus one taxi lane", links, 3);
		assertMaxspeed(links, 70);
	}

	@Test
	public void testDefaultResidentialOneway() {
		Set<Link> links = osmid2link.get(7994914L);
		assertEquals("oneway", 1, links.size());
		assertEquals("oneway up north", 1, getLinksTowardsNode(links, 59836794L).size());
		assertLanes(links, 1);
		assertMaxspeed("taken from OsmConverterConfigGroup.createDefaultConfig", links, 15);
	}
	
	@Test
	public void testResidentialInvalidLanesAndMaxspeed() {
		Set<Link> links = osmid2link.get(7994891L);
		assertEquals("bidirectional", 2, links.size());
		assertLanes("taken from OsmConverterConfigGroup.createDefaultConfig", links, 1);
		assertMaxspeed("taken from OsmConverterConfigGroup.createDefaultConfig", links, 15);
	}
	

	@Test
	public void testDefaultPrimaryOneway() {
		Set<Link> links = osmid2link.get(7994919L);
		assertEquals("oneway", 1, links.size());
		assertEquals("oneway up north", 1, getLinksTowardsNode(links, 59836804L).size());
		assertLanes("taken from OsmConverterConfigGroup.createDefaultConfig", links, 1);
		assertMaxspeed("taken from OsmConverterConfigGroup.createDefaultConfig", links, 80);
	}

	@Test
	public void testPrimaryOnewayWithLanesAndMaxspeed() {
		Set<Link> links = osmid2link.get(240536138L);
		assertEquals("oneway", 1, links.size());
		assertEquals("oneway up north", 1, getLinksTowardsNode(links, 2482638327L).size());
		assertLanes(links, 3);
		assertMaxspeed(links, 70);
	}

	@Test
	public void testPrimaryOnewayWithForwardLanesAndMaxspeed() {
		Set<Link> links = osmid2link.get(7994920L);
		assertEquals("oneway", 1, links.size());
		assertEquals("oneway up north", 1, getLinksTowardsNode(links, 59836807L).size());
		assertLanes(links, 3);
		assertMaxspeed(links, 70);
	}

	@Test
	public void testPrimaryOnewayWithForwardSpecialLanesAndMaxspeed() {
		Set<Link> links = osmid2link.get(7994925L);
		assertEquals("oneway", 1, links.size());
		assertEquals("oneway up north", 1, getLinksTowardsNode(links, 59836816L).size());
		assertLanes("4 minus one bus lane", links, 3);
		assertMaxspeed(links, 70);
	}

	@Test
	public void testPrimaryOnewayWithSpecialLane() {
		Set<Link> links = osmid2link.get(7994927L);
		assertEquals("oneway", 1, links.size());
		assertEquals("oneway up north", 1, getLinksTowardsNode(links, 59836820L).size());
		assertLanes("4 minus one bus lane", links, 3);
		assertMaxspeed(links, 70);
	}
	
	@Test
	public void testPrimaryDefaultReversedOneway() {
		Set<Link> links = osmid2link.get(7994930L);
		assertEquals("oneway", 1, links.size());
		assertEquals("oneway down south", 1, getLinksTowardsNode(links, 59836834L).size());
		assertLanes(links, 3);
		assertMaxspeed(links, 70);
	}
	
	@Test
	public void testMotorwayWithoutMaxspeedAndOneway() {
		Set<Link> links = osmid2link.get(7994932L);
		assertEquals("oneway by default - taken from OsmConverterConfigGroup.createDefaultConfig", 1, links.size());
		assertEquals("oneway up north", 1, getLinksTowardsNode(links, 59836844L).size());
		assertLanes("taken from OsmConverterConfigGroup.createDefaultConfig", links, 2);
		assertMaxspeed(links, OsmMultimodalNetworkConverter.SPEED_LIMIT_NONE_KPH);
	}
	
	@Test
	public void testResidentialWithMaxspeedWalk() {
		Set<Link> links = osmid2link.get(7994934L);
		assertEquals("bidirectional", 2, links.size());
		assertMaxspeed(links, OsmMultimodalNetworkConverter.SPEED_LIMIT_WALK_KPH);
	}
	
	@Test
	public void testResidentialWithMaxspeedMiles() {
		Set<Link> links = osmid2link.get(7994935L);
		assertEquals("bidirectional", 2, links.size());
		assertMaxspeed(links, 20 * 1.609344);
	}
	
	@Test
	public void testResidentialWithMaxspeedKnots() {
		Set<Link> links = osmid2link.get(7994935L);
		assertEquals("bidirectional", 2, links.size());
		assertMaxspeed(links, 20 * 1.609344);
	}
	
	@Test
	public void testResidentialMultipleSpeedLimits() {
		Set<Link> links = osmid2link.get(7999581L);
		assertEquals("bidirectional", 2, links.size());
		assertMaxspeed("second speed limit is ignored", links, 40);
	}
	
	private static void assertLanes(Set<Link> links, double expectedLanes) {
		assertLanes("", links, expectedLanes);
	}

	private static void assertLanes(String message, Set<Link> links, double expectedLanes) {
		assertFalse("at least one link expected", links.isEmpty());
		for (Link link : links) {
			assertEquals("lanes (in one direction): " + message, expectedLanes, link.getNumberOfLanes(), DELTA);
		}
	}
	
	private static void assertMaxspeed(Set<Link> links, double expectedFreespeedKph) {
		assertMaxspeed("", links, expectedFreespeedKph);
	}

	private static void assertMaxspeed(String message, Set<Link> links, double expectedFreespeedKph) {
		assertFalse("at least one link expected", links.isEmpty());
		for (Link link : links) {
			assertEquals("freespeed m/s: message", expectedFreespeedKph / 3.6, link.getFreespeed(), DELTA);
		}
	}

	@Test
	public void convertWaterlooCityCentre() {
		// setup config
		OsmConverterConfigGroup osmConfig = OsmConverterConfigGroup.createDefaultConfig();
		osmConfig.setOutputCoordinateSystem("WGS84");
		osmConfig.setOsmFile("test/osm/WaterlooCityCentre.osm");
		osmConfig.setOutputNetworkFile("test/output/WaterlooCityCentre.xml.gz");
		osmConfig.setMaxLinkLength(20);

		// read OSM file
		OsmData osm = new OsmDataImpl();
		new OsmFileReader(osm).readFile(osmConfig.getOsmFile());

		// convert
		OsmMultimodalNetworkConverter converter = new OsmMultimodalNetworkConverter(osm);
		converter.convert(osmConfig);

		// write file
		// NetworkTools.writeNetwork(converter.getNetwork(), osmConfig.getOutputNetworkFile());
	}

}