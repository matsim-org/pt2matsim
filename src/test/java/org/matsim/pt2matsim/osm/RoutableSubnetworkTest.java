package org.matsim.pt2matsim.osm;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.config.ConfigGroup;
import org.matsim.core.network.NetworkUtils;
import org.matsim.pt2matsim.config.OsmConverterConfigGroup;
import org.matsim.pt2matsim.osm.lib.OsmData;
import org.matsim.pt2matsim.osm.lib.OsmDataImpl;
import org.matsim.pt2matsim.osm.lib.OsmFileReader;

import java.util.HashSet;
import java.util.Set;

/**
 * @author shoerl
 */
class RoutableSubnetworkTest {

	@Test
	void customSubnetworks() {
		// setup config
		OsmConverterConfigGroup osmConfig = OsmConverterConfigGroup.createDefaultConfig();
		osmConfig.setOutputCoordinateSystem("WGS84");
		osmConfig.setOsmFile("test/osm/routable_subnetwork.osm");
		osmConfig.setOutputNetworkFile("test/output/routable_subnetwork.xml");
		osmConfig.setMaxLinkLength(20);

		// Add car_passenger to all car links
		for (ConfigGroup params : osmConfig.getParameterSets(OsmConverterConfigGroup.OsmWayParams.SET_NAME)) {
			OsmConverterConfigGroup.OsmWayParams wayParams = (OsmConverterConfigGroup.OsmWayParams) params;

			if (wayParams.getAllowedTransportModes().contains("car")) {
				Set<String> allowedTransportModes = new HashSet<>(wayParams.getAllowedTransportModes());
				allowedTransportModes.add("car_passenger");
				wayParams.setAllowedTransportModes(allowedTransportModes);
			}
		}

		// read OSM file
		OsmData osm = new OsmDataImpl();
		new OsmFileReader(osm).readFile(osmConfig.getOsmFile());

		// I) Convert without a network layer for car_passenger
		OsmMultimodalNetworkConverter converter = new OsmMultimodalNetworkConverter(osm);
		converter.convert(osmConfig);

		Network network1 = converter.getNetwork();
		// NetworkUtils.writeNetwork(network1, "1.xml");

		// Since some car links are not connected, we expect that there are more (uncleaned) passenger links now
		Link l2 = network1.getLinks().get(Id.createLinkId("2"));
		Assertions.assertEquals(Set.of("car_passenger"), l2.getAllowedModes());
		int carLinks = countModeLinks(network1, "car");
		int carPassengerLinks = countModeLinks(network1, "car_passenger");
		Assertions.assertEquals(carLinks + 1, carPassengerLinks);

		// II) Convert with a network layer for car_passenger
		osmConfig.addParameterSet(new OsmConverterConfigGroup.RoutableSubnetworkParams("car_passenger", Set.of("car")));

		OsmMultimodalNetworkConverter converter2 = new OsmMultimodalNetworkConverter(osm);
		converter2.convert(osmConfig);
		Network network2 = converter2.getNetwork();
		// NetworkUtils.writeNetwork(network2, "2.xml");

		NetworkUtils.cleanNetwork(network2, Set.of("car"));
		// NetworkUtils.writeNetwork(network2, "3.xml");

		int carLinks2 = countModeLinks(network2, "car");
		int carPassengerLinks2 = countModeLinks(network2, "car_passenger");

		System.out.println("carLinks: " + carLinks);
		System.out.println("carPassengerLinks: " + carPassengerLinks);
		System.out.println("carLinks2: " + carLinks2);
		System.out.println("carPassengerLinks2: " + carPassengerLinks2);

		// Now car_passenger should be cleaned just as car... Thus it should be the same number.
		Assertions.assertEquals(carLinks2, carPassengerLinks2);
	}

	@Test
	void customSubnetworksWaterloo() {
		// setup config
		OsmConverterConfigGroup osmConfig = OsmConverterConfigGroup.createDefaultConfig();
		osmConfig.setOutputCoordinateSystem("WGS84");
		osmConfig.setOsmFile("test/osm/WaterlooCityCentre.osm");
		osmConfig.setOutputNetworkFile("test/output/WaterlooCityCentre.xml.gz");
		osmConfig.setMaxLinkLength(20);

		// Add car_passenger to all car links
		for (ConfigGroup params : osmConfig.getParameterSets(OsmConverterConfigGroup.OsmWayParams.SET_NAME)) {
			OsmConverterConfigGroup.OsmWayParams wayParams = (OsmConverterConfigGroup.OsmWayParams) params;

			if (wayParams.getAllowedTransportModes().contains("car")) {
				Set<String> allowedTransportModes = new HashSet<>(wayParams.getAllowedTransportModes());
				allowedTransportModes.add("car_passenger");
				wayParams.setAllowedTransportModes(allowedTransportModes);
			}
		}

		// read OSM file
		OsmData osm = new OsmDataImpl();
		new OsmFileReader(osm).readFile(osmConfig.getOsmFile());

		// I) Convert without a network layer for car_passenger
		OsmMultimodalNetworkConverter converter = new OsmMultimodalNetworkConverter(osm);
		converter.convert(osmConfig);

		// Since some car links are not connected, we expect that there are more (uncleaned) passenger links now
		int carLinks = countModeLinks(converter.getNetwork(), "car");
		int carPassengerLinks = countModeLinks(converter.getNetwork(), "car_passenger");
		Assertions.assertEquals(carLinks + 8, carPassengerLinks);
		
		// II) Convert with a network layer for car_passenger
		osmConfig.addParameterSet(new OsmConverterConfigGroup.RoutableSubnetworkParams("car_passenger", Set.of("car")));
		
		OsmMultimodalNetworkConverter converter2 = new OsmMultimodalNetworkConverter(osm);
		converter2.convert(osmConfig);
		Network network2 = converter2.getNetwork();
		// NetworkUtils.writeNetwork(network2, "waterloo2.xml");

		NetworkUtils.cleanNetwork(network2, Set.of("car"));

		int carLinks2 = countModeLinks(network2, "car");
		int carPassengerLinks2 = countModeLinks(network2, "car_passenger");

		System.out.println("carLinks: " + carLinks);
		System.out.println("carPassengerLinks: " + carPassengerLinks);
		System.out.println("carLinks2: " + carLinks2);
		System.out.println("carPassengerLinks2: " + carPassengerLinks2);

		// Now car_passenger should be cleaned just as car... Thus it should be the same number.
		Assertions.assertEquals(carLinks2, carPassengerLinks2);
	}
	
	private static int countModeLinks(Network network, String mode) {
		int count = 0;
		
		for (Link link : network.getLinks().values()) {
			if (link.getAllowedModes().contains(mode)) {
				count++;
			}
		}
		
		return count;
	}
}
