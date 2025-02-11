package org.matsim.pt2matsim.osm;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
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

class OsmMultimodalNetworkConverterTurnRestrictionsTest {

	private static Network network;

	@BeforeAll
	static void convertRudolfplatz() {
		// setup config
		OsmConverterConfigGroup osmConfig = OsmConverterConfigGroup.createDefaultConfig();
		osmConfig.setOutputCoordinateSystem("EPSG:25832");
		osmConfig.setOsmFile("test/osm/Rudolfplatz.osm");
		osmConfig.setOutputNetworkFile("test/osm/Rudolfplatz.xml.gz");
		osmConfig.setMaxLinkLength(1000);
		osmConfig.parseTurnRestrictions = true;

		// read OSM file
		OsmData osm = new OsmDataImpl();
		new OsmFileReader(osm).readFile(osmConfig.getOsmFile());

		// convert
		OsmMultimodalNetworkConverter converter = new OsmMultimodalNetworkConverter(osm);
		converter.convert(osmConfig);

		network = converter.getNetwork();

		// write file
		// NetworkTools.writeNetwork(network, osmConfig.getOutputNetworkFile());
	}

	@Test
	void testisValid() {

		Assertions.assertTrue(DisallowedNextLinksUtils.isValid(network));

	}

	@Test
	void testDisallowedNextLinks() {

		Id<Link> lId124 = Id.createLinkId("124");
		Link l124 = network.getLinks().get(lId124);
		DisallowedNextLinks dnl = NetworkUtils.getDisallowedNextLinks(l124);

		Assertions.assertEquals(Map.of(
				"bus", List.of(List.of(Id.createLinkId("68"), Id.createLinkId("414"))),
				TransportMode.car, List.of(List.of(Id.createLinkId("68"), Id.createLinkId("414"))),
				TransportMode.pt, List.of(List.of(Id.createLinkId("68"), Id.createLinkId("414")))), dnl.getAsMap());

	}

}
