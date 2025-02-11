package org.matsim.pt2matsim.osm;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.config.Configurator;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.turnRestrictions.DisallowedNextLinks;
import org.matsim.core.network.turnRestrictions.DisallowedNextLinksUtils;
import org.matsim.pt2matsim.config.OsmConverterConfigGroup;
import org.matsim.pt2matsim.osm.lib.OsmData;
import org.matsim.pt2matsim.osm.lib.OsmDataImpl;
import org.matsim.pt2matsim.osm.lib.OsmFileReader;
import org.matsim.pt2matsim.tools.NetworkTools;

class OsmMultimodalNetworkConverterTurnRestrictionsTest {

	private static final boolean DEBUG = false;

	@BeforeEach
	void setLogLevel() {
		if (DEBUG) {
			Configurator.setLevel(LogManager.getLogger(OsmMultimodalNetworkConverter.class),
					org.apache.logging.log4j.Level.DEBUG);
		}
	}

	@Test
	void test0Valid() {

		Network network = convert("tr0_valid.osm");

		// --------------------------------------------------------------------

		Assertions.assertTrue(DisallowedNextLinksUtils.isValid(network));

		long noOfDnl = network.getLinks().values().stream()
				.map(NetworkUtils::getDisallowedNextLinks)
				.filter(Objects::nonNull)
				.count();
		Assertions.assertEquals(1L, noOfDnl);

		DisallowedNextLinks dnl = NetworkUtils.getDisallowedNextLinks(network.getLinks().get(Id.createLinkId("1")));
		Assertions.assertNotNull(dnl);

		Assertions.assertEquals(List.of(List.of(Id.createLinkId("7"), Id.createLinkId("9"))),
				dnl.getDisallowedLinkSequences("car"));
	}

	@Test
	void test1Invalid() {

		// This OSM network has an invalid turn restriction relation. The to-way does
		// not start or end at the via-node.
		Network network = convert("tr1_invalid.osm");

		// --------------------------------------------------------------------

		Assertions.assertTrue(DisallowedNextLinksUtils.isValid(network));

		long noOfDnl = network.getLinks().values().stream()
				.map(NetworkUtils::getDisallowedNextLinks)
				.filter(Objects::nonNull)
				.count();
		Assertions.assertEquals(0L, noOfDnl);
	}

	@Test
	void test2Valid() {

		// This is a uturn, where from & to is the same way
		// It needs to be ensured, that only one Turn restriction is created at the via
		// node.
		Network network = convert("tr2_valid.osm");

		// --------------------------------------------------------------------

		Assertions.assertTrue(DisallowedNextLinksUtils.isValid(network));

		long noOfDnl = network.getLinks().values().stream()
				.map(NetworkUtils::getDisallowedNextLinks)
				.filter(Objects::nonNull)
				.count();
		Assertions.assertEquals(1L, noOfDnl);

		DisallowedNextLinks dnl1 = NetworkUtils.getDisallowedNextLinks(network.getLinks().get(Id.createLinkId("1")));
		Assertions.assertNotNull(dnl1);
		DisallowedNextLinks dnl2 = NetworkUtils.getDisallowedNextLinks(network.getLinks().get(Id.createLinkId("2")));
		Assertions.assertNull(dnl2);

		Assertions.assertEquals(List.of(List.of(Id.createLinkId("2"))), dnl1.getDisallowedLinkSequences("car"));
	}

	@Test
	void test3Valid() {

		// This is a uturn, where from & to is the same way and the way is long so two
		// links are created
		Network network = convert("tr3_valid.osm");

		// --------------------------------------------------------------------

		Assertions.assertTrue(DisallowedNextLinksUtils.isValid(network));

		long noOfDnl = network.getLinks().values().stream()
				.map(NetworkUtils::getDisallowedNextLinks)
				.filter(Objects::nonNull)
				.count();
		Assertions.assertEquals(1L, noOfDnl);

		DisallowedNextLinks dnl = NetworkUtils.getDisallowedNextLinks(network.getLinks().get(Id.createLinkId("9")));
		Assertions.assertNotNull(dnl);

		Assertions.assertEquals(List.of(List.of(Id.createLinkId("10"), Id.createLinkId("8"))),
				dnl.getDisallowedLinkSequences("car"));
	}

	@Test
	void test4Valid() {

		// This is a uturn, where from & to are different ways and via is also a way,
		// all one-way
		Network network = convert("tr4_valid.osm");

		// --------------------------------------------------------------------

		Assertions.assertTrue(DisallowedNextLinksUtils.isValid(network));

		long noOfDnl = network.getLinks().values().stream()
				.map(NetworkUtils::getDisallowedNextLinks)
				.filter(Objects::nonNull)
				.count();
		Assertions.assertEquals(1L, noOfDnl);

		DisallowedNextLinks dnl = NetworkUtils.getDisallowedNextLinks(network.getLinks().get(Id.createLinkId("8")));
		Assertions.assertNotNull(dnl);

		Assertions.assertEquals(List.of(List.of(Id.createLinkId("10"), Id.createLinkId("7"))),
				dnl.getDisallowedLinkSequences("car"));
	}

	@Test
	void test5Valid() {

		// This is a uturn, where from & to are different ways and via is also a way,
		// all one-way and long links
		Network network = convert("tr5_valid.osm");

		// --------------------------------------------------------------------

		Assertions.assertTrue(DisallowedNextLinksUtils.isValid(network));

		long noOfDnl = network.getLinks().values().stream()
				.map(NetworkUtils::getDisallowedNextLinks)
				.filter(Objects::nonNull)
				.count();
		Assertions.assertEquals(1L, noOfDnl);

		DisallowedNextLinks dnl = NetworkUtils.getDisallowedNextLinks(network.getLinks().get(Id.createLinkId("9")));
		Assertions.assertNotNull(dnl);

		Assertions.assertEquals(List.of(List.of(Id.createLinkId("11"), Id.createLinkId("7"))),
				dnl.getDisallowedLinkSequences("car"));
	}

	@Test
	void test6Valid() {

		// only straight is allowed
		Network network = convert("tr6_valid.osm");

		// --------------------------------------------------------------------

		Assertions.assertTrue(DisallowedNextLinksUtils.isValid(network));

		long noOfDnl = network.getLinks().values().stream()
				.map(NetworkUtils::getDisallowedNextLinks)
				.filter(Objects::nonNull)
				.count();
		Assertions.assertEquals(1L, noOfDnl);

		DisallowedNextLinks dnl = NetworkUtils.getDisallowedNextLinks(network.getLinks().get(Id.createLinkId("3")));
		Assertions.assertNotNull(dnl);

		Assertions.assertEquals(List.of(
				List.of(Id.createLinkId("4")), List.of(Id.createLinkId("6")), List.of(Id.createLinkId("8"))),
				dnl.getDisallowedLinkSequences("car"));
	}

	@Test
	void testRudolfplatz() {

		Network network = convert("Rudolfplatz.osm");

		// --------------------------------------------------------------------

		Id<Link> lId124 = Id.createLinkId("124");
		Link l124 = network.getLinks().get(lId124);
		DisallowedNextLinks dnl = NetworkUtils.getDisallowedNextLinks(l124);

		Assertions.assertTrue(DisallowedNextLinksUtils.isValid(network));

		long noOfDnl = network.getLinks().values().stream()
				.map(NetworkUtils::getDisallowedNextLinks)
				.filter(Objects::nonNull)
				.count();
		Assertions.assertEquals(11L, noOfDnl);

		Assertions.assertEquals(Map.of(
				"bus", List.of(List.of(Id.createLinkId("68"), Id.createLinkId("414"))),
				TransportMode.car, List.of(List.of(Id.createLinkId("68"), Id.createLinkId("414"))),
				TransportMode.pt, List.of(List.of(Id.createLinkId("68"), Id.createLinkId("414")))), dnl.getAsMap());
	}

	// Helpers

	static Network convert(String filename) {

		// setup config
		OsmConverterConfigGroup osmConfig = OsmConverterConfigGroup.createDefaultConfig();
		osmConfig.setOutputCoordinateSystem("EPSG:25832");
		osmConfig.setOsmFile("test/osm/" + filename);
		osmConfig.setOutputNetworkFile("test/osm/" + filename.replace(".osm", "") + ".xml");
		osmConfig.setMaxLinkLength(1000);
		osmConfig.parseTurnRestrictions = true;

		// read OSM file
		OsmData osm = new OsmDataImpl();
		new OsmFileReader(osm).readFile(osmConfig.getOsmFile());

		// convert
		OsmMultimodalNetworkConverter converter = new OsmMultimodalNetworkConverter(osm);
		converter.convert(osmConfig);

		Network network = converter.getNetwork();

		// write file
		if (DEBUG) {
			NetworkTools.writeNetwork(network, osmConfig.getOutputNetworkFile());
		}

		return network;
	}

}
