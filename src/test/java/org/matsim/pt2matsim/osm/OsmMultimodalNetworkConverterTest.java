package org.matsim.pt2matsim.osm;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.pt2matsim.config.OsmConverterConfigGroup;
import org.matsim.pt2matsim.osm.lib.OsmData;
import org.matsim.pt2matsim.osm.lib.OsmDataImpl;
import org.matsim.pt2matsim.osm.lib.OsmFileReader;
import org.matsim.pt2matsim.run.CreateDefaultOsmConfig;

/**
 * @author polettif
 * @author mstraub - Austrian Institute of Technology
 */
public class OsmMultimodalNetworkConverterTest {
	/** MATSim links created from the Gerasdorf test file */
	private static Map<Long, Set<Link>> osmid2link;
	private static final double DELTA = 0.001;
	
	@BeforeAll
	public static void convertGerasdorfArtificialLanesAndMaxspeed() {
		// setup config
		OsmConverterConfigGroup osmConfig = OsmConverterConfigGroup.createDefaultConfig();
		osmConfig.outputCoordinateSystem = "EPSG:31256";
		osmConfig.osmFile = "test/osm/GerasdorfArtificialLanesAndMaxspeed.osm";
		osmConfig.outputNetworkFile = "test/osm/GerasdorfArtificialLanesAndMaxspeed.xml.gz";
		osmConfig.maxLinkLength = 1000;

		// read OSM file
		OsmData osm = new OsmDataImpl();
		new OsmFileReader(osm).readFile(osmConfig.osmFile);

		// convert
		OsmMultimodalNetworkConverter converter = new OsmMultimodalNetworkConverter(osm);
		converter.convert(osmConfig);

		Network network = converter.getNetwork();
		
		// write file NetworkTools.writeNetwork(network, osmConfig.outputNetworkFile);
		
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
	void testDefaultResidential() {
		Set<Link> links = osmid2link.get(7994891L);
		Assertions.assertEquals(2, links.size(), "bidirectional");
		assertLanes(links, 1);
		assertMaxspeed("taken from OsmConverterConfigGroup.createDefaultConfig", links, 15);
	}
	
	@Test
	void testDefaultPrimary() {
		Set<Link> links = osmid2link.get(7994890L);
		Assertions.assertEquals(2, links.size(), "bidirectional");
		assertLanes("taken from OsmConverterConfigGroup.createDefaultConfig", links, 1);
		assertMaxspeed("taken from OsmConverterConfigGroup.createDefaultConfig", links, 80);
	}
	
	@Test
	void testPrimaryWithLanesAndMaxspeed() {
		Set<Link> links = osmid2link.get(7994889L);
		Assertions.assertEquals(2, links.size(), "bidirectional");
		assertLanes(links, 3);
		assertMaxspeed(links, 70);
	}

	@Test
	void testPrimaryWithOddLanesAndMaxspeed() {
		Set<Link> links = osmid2link.get(7994888L);
		Assertions.assertEquals(2, links.size(), "bidirectional");
		assertLanes(links, 3.5);
		assertMaxspeed(links, 70);
	}

	@Test
	void testPrimaryWithForwardAndBackwardLanesAndMaxspeed() {
		Set<Link> links = osmid2link.get(7994887L);
		Assertions.assertEquals(2, links.size(), "bidirectional");

		Set<Link> linksToNorth = getLinksTowardsNode(links, 59836731L);
		assertLanes(linksToNorth, 3);
		assertMaxspeed(linksToNorth, 70);

		Set<Link> linksToSouth = getLinksTowardsNode(links, 59836730L);
		assertLanes(linksToSouth, 4);
		assertMaxspeed(linksToSouth, 100);
	}
	
	@Test
	void testPrimaryWithForwardAndBackwardSpecialLanesAndMaxspeed() {
		Set<Link> links = osmid2link.get(7994886L);
		Assertions.assertEquals(2, links.size(), "bidirectional");

		Set<Link> linksToNorth = getLinksTowardsNode(links, 57443579L);
		assertLanes("4 minus one bus lane", linksToNorth, 3);
		assertMaxspeed(linksToNorth, 70);

		Set<Link> linksToSouth = getLinksTowardsNode(links, 59836729L);
		assertLanes("5 minus one psv lane", linksToSouth, 4);
		assertMaxspeed(linksToSouth, 100);
	}

	@Test
	void testPrimaryWithSpecialLanes() {
		Set<Link> links = osmid2link.get(7994912L);
		Assertions.assertEquals(2, links.size(), "bidirectional");
		assertLanes("4 per direction minus one taxi lane", links, 3);
		assertMaxspeed(links, 70);
	}

	@Test
	void testDefaultResidentialOneway() {
		Set<Link> links = osmid2link.get(7994914L);
		Assertions.assertEquals(1, links.size(), "oneway");
		Assertions.assertEquals(1, getLinksTowardsNode(links, 59836794L).size(), "oneway up north");
		assertLanes(links, 1);
		assertMaxspeed("taken from OsmConverterConfigGroup.createDefaultConfig", links, 15);
	}
	
	@Test
	void testResidentialInvalidLanesAndMaxspeed() {
		Set<Link> links = osmid2link.get(7994891L);
		Assertions.assertEquals(2, links.size(), "bidirectional");
		assertLanes("taken from OsmConverterConfigGroup.createDefaultConfig", links, 1);
		assertMaxspeed("taken from OsmConverterConfigGroup.createDefaultConfig", links, 15);
	}
	

	@Test
	void testDefaultPrimaryOneway() {
		Set<Link> links = osmid2link.get(7994919L);
		Assertions.assertEquals(1, links.size(), "oneway");
		Assertions.assertEquals(1, getLinksTowardsNode(links, 59836804L).size(), "oneway up north");
		assertLanes("taken from OsmConverterConfigGroup.createDefaultConfig", links, 1);
		assertMaxspeed("taken from OsmConverterConfigGroup.createDefaultConfig", links, 80);
	}

	@Test
	void testPrimaryOnewayWithLanesAndMaxspeed() {
		Set<Link> links = osmid2link.get(240536138L);
		Assertions.assertEquals(1, links.size(), "oneway");
		Assertions.assertEquals(1, getLinksTowardsNode(links, 2482638327L).size(), "oneway up north");
		assertLanes(links, 3);
		assertMaxspeed(links, 70);
	}

	@Test
	void testPrimaryOnewayWithForwardLanesAndMaxspeed() {
		Set<Link> links = osmid2link.get(7994920L);
		Assertions.assertEquals(1, links.size(), "oneway");
		Assertions.assertEquals(1, getLinksTowardsNode(links, 59836807L).size(), "oneway up north");
		assertLanes(links, 3);
		assertMaxspeed(links, 70);
	}

	@Test
	void testPrimaryOnewayWithForwardSpecialLanesAndMaxspeed() {
		Set<Link> links = osmid2link.get(7994925L);
		Assertions.assertEquals(1, links.size(), "oneway");
		Assertions.assertEquals(1, getLinksTowardsNode(links, 59836816L).size(), "oneway up north");
		assertLanes("4 minus one bus lane", links, 3);
		assertMaxspeed(links, 70);
	}

	@Test
	void testPrimaryOnewayWithSpecialLane() {
		Set<Link> links = osmid2link.get(7994927L);
		Assertions.assertEquals(1, links.size(), "oneway");
		Assertions.assertEquals(1, getLinksTowardsNode(links, 59836820L).size(), "oneway up north");
		assertLanes("4 minus one bus lane", links, 3);
		assertMaxspeed(links, 70);
	}
	
	@Test
	void testPrimaryDefaultReversedOneway() {
		Set<Link> links = osmid2link.get(7994930L);
		Assertions.assertEquals(1, links.size(), "oneway");
		Assertions.assertEquals(1, getLinksTowardsNode(links, 59836834L).size(), "oneway down south");
		assertLanes(links, 3);
		assertMaxspeed(links, 70);
	}
	
	@Test
	void testMotorwayWithoutMaxspeedAndOneway() {
		Set<Link> links = osmid2link.get(7994932L);
		Assertions.assertEquals(1, links.size(),
				"oneway by default - taken from OsmConverterConfigGroup.createDefaultConfig");
		Assertions.assertEquals(1, getLinksTowardsNode(links, 59836844L).size(), "oneway up north");
		assertLanes("taken from OsmConverterConfigGroup.createDefaultConfig", links, 2);
		assertMaxspeed(links, OsmMultimodalNetworkConverter.SPEED_LIMIT_NONE_KPH);
	}
	
	@Test
	void testResidentialWithMaxspeedWalk() {
		Set<Link> links = osmid2link.get(7994934L);
		Assertions.assertEquals(2, links.size(), "bidirectional");
		assertMaxspeed(links, OsmMultimodalNetworkConverter.SPEED_LIMIT_WALK_KPH);
	}
	
	@Test
	void testResidentialWithMaxspeedMiles() {
		Set<Link> links = osmid2link.get(7994935L);
		Assertions.assertEquals(2, links.size(), "bidirectional");
		assertMaxspeed(links, 20 * 1.609344);
	}
	
	@Test
	void testResidentialWithMaxspeedKnots() {
		Set<Link> links = osmid2link.get(7994935L);
		Assertions.assertEquals(2, links.size(), "bidirectional");
		assertMaxspeed(links, 20 * 1.609344);
	}
	
	@Test
	void testResidentialMultipleSpeedLimits() {
		Set<Link> links = osmid2link.get(7999581L);
		Assertions.assertEquals(2, links.size(), "bidirectional");
		assertMaxspeed("second speed limit is ignored", links, 40);
	}
	
	@Test
	void testDeadEndStreetsAreContainedInNetwork() {
		Assertions.assertEquals(2, osmid2link.get(22971704L).size());
		Assertions.assertEquals(2, osmid2link.get(153227314L).size());
		Assertions.assertEquals(2, osmid2link.get(95142433L).size());
		Assertions.assertEquals(2, osmid2link.get(95142441L).size());
	}
	
	private static void assertLanes(Set<Link> links, double expectedLanes) {
		assertLanes("", links, expectedLanes);
	}

	private static void assertLanes(String message, Set<Link> links, double expectedLanes) {
		Assertions.assertFalse(links.isEmpty(), "at least one link expected");
		for (Link link : links) {
			Assertions.assertEquals(expectedLanes, link.getNumberOfLanes(), DELTA,
					"lanes (in one direction): " + message);
		}
	}
	
	private static void assertMaxspeed(Set<Link> links, double expectedFreespeedKph) {
		assertMaxspeed("", links, expectedFreespeedKph);
	}

	private static void assertMaxspeed(String message, Set<Link> links, double expectedFreespeedKph) {
		Assertions.assertFalse(links.isEmpty(), "at least one link expected");
		for (Link link : links) {
			Assertions.assertEquals(expectedFreespeedKph / 3.6, link.getFreespeed(), DELTA,
					"freespeed m/s: " + message);
		}
	}

	@Test
	void convertWaterlooCityCentre() {
		// setup config
		OsmConverterConfigGroup osmConfig = OsmConverterConfigGroup.createDefaultConfig();
		osmConfig.outputCoordinateSystem = "WGS84";
		osmConfig.osmFile = "test/osm/WaterlooCityCentre.osm";
		osmConfig.outputNetworkFile = "test/output/WaterlooCityCentre.xml.gz";
		osmConfig.maxLinkLength = 20;

		// read OSM file
		OsmData osm = new OsmDataImpl();
		new OsmFileReader(osm).readFile(osmConfig.osmFile);

		// convert
		OsmMultimodalNetworkConverter converter = new OsmMultimodalNetworkConverter(osm);
		converter.convert(osmConfig);

		// write file
		// NetworkTools.writeNetwork(converter.getNetwork(), osmConfig.outputNetworkFile);
	}

	@Test
	void convertEPSG() {
		OsmConverterConfigGroup osmConfig = OsmConverterConfigGroup.createDefaultConfig();
		osmConfig.outputCoordinateSystem = "EPSG:8682";
		osmConfig.osmFile = "test/osm/Belgrade.osm";

		OsmData osm = new OsmDataImpl();
		new OsmFileReader(osm).readFile(osmConfig.osmFile);

		OsmMultimodalNetworkConverter converter = new OsmMultimodalNetworkConverter(osm);
		converter.convert(osmConfig);
	}

	@Test
	void defaultConfig() {
		CreateDefaultOsmConfig.main(new String[]{"doc/defaultOsmConfig.xml"});
	}

}