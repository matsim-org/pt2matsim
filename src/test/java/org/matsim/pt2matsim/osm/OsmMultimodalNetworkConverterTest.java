package org.matsim.pt2matsim.osm;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.pt2matsim.config.OsmConverterConfigGroup;
import org.matsim.pt2matsim.osm.lib.OsmData;
import org.matsim.pt2matsim.osm.lib.OsmDataImpl;
import org.matsim.pt2matsim.osm.lib.OsmFileReader;
import org.matsim.pt2matsim.tools.NetworkTools;

/**
 * @author polettif
 * @author mstraub - Austrian Institute of Technology
 */
public class OsmMultimodalNetworkConverterTest {
	private static Map<Long, Set<Link>> osmid2link;
	private static final double DELTA = 0.000001;
	
	@BeforeClass
	public static void convertGerasdorfArtificialLanesAndMaxspeed() {
		// setup config
		OsmConverterConfigGroup osmConfig = OsmConverterConfigGroup.createDefaultConfig();
		osmConfig.setOutputCoordinateSystem("EPSG:31256");
		osmConfig.setOsmFile("test/osm/GerasdorfArtificialLanesAndMaxspeed.osm");
		osmConfig.setOutputNetworkFile("test/osm/GerasdorfArtificialLanesAndMaxspeed.xml.gz");

		// read osm file
		OsmData osm = new OsmDataImpl();
		new OsmFileReader(osm).readFile(osmConfig.getOsmFile());

		// convert
		OsmMultimodalNetworkConverter converter = new OsmMultimodalNetworkConverter(osm);
		converter.convert(osmConfig);

		Network network = converter.getNetwork();
		
		// write file
		//NetworkTools.writeNetwork(network, osmConfig.getOutputNetworkFile());
		NetworkTools.writeNetwork(network, "/tmp/network.xml");
		
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
	
	@Test
	public void testDefaultResidential() {
		Set<Link> links = osmid2link.get(7994891L);
		Assert.assertEquals("bidirectional", 2, links.size());
		for (Link link : links) {
			//Assert.assertEquals(50 / 3.6, link.getFreespeed(), DELTA);
			Assert.assertEquals(1, link.getNumberOfLanes(), DELTA);
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

		// read osm file
		OsmData osm = new OsmDataImpl();
		new OsmFileReader(osm).readFile(osmConfig.getOsmFile());

		// convert
		OsmMultimodalNetworkConverter converter = new OsmMultimodalNetworkConverter(osm);
		converter.convert(osmConfig);

		// write file
		// NetworkTools.writeNetwork(converter.getNetwork(), osmConfig.getOutputNetworkFile());
	}

}