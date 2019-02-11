package org.matsim.pt2matsim.osm;

import org.junit.Test;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.config.ConfigGroup;
import org.matsim.pt2matsim.config.OsmConverterConfigGroup;
import org.matsim.pt2matsim.osm.lib.OsmData;
import org.matsim.pt2matsim.osm.lib.OsmDataImpl;
import org.matsim.pt2matsim.osm.lib.OsmFileReader;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;

/**
 * @author shoerl
 */
public class RoutableSubnetworkTest {

	@Test
	public void customSubnetworks() {
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
		
		int carLinks = countModeLinks(converter.getNetwork(), "car");
		int carPassengerLinks = countModeLinks(converter.getNetwork(), "car_passenger");
		
		// Since some car links are not connected, we expect that there are more (uncleaned) passenger links now
		assertEquals(carLinks + 8, carPassengerLinks);
		
		// II) Convert with a network layer for car_passenger
		osmConfig.addParameterSet(new OsmConverterConfigGroup.RoutableSubnetworkParams("car_passenger", Collections.singleton("car")));
		
		OsmMultimodalNetworkConverter converter2 = new OsmMultimodalNetworkConverter(osm);
		converter2.convert(osmConfig);
		
		int carLinks2 = countModeLinks(converter2.getNetwork(), "car");
		int carPassengerLinks2 = countModeLinks(converter2.getNetwork(), "car_passenger");
		
		// Now car_passenger should be cleaned just as car... Thus it should be the same number.
		assertEquals(carLinks2, carPassengerLinks2);
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
