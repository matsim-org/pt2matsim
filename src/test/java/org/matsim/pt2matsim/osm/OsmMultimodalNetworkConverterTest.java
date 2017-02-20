package org.matsim.pt2matsim.osm;

import org.junit.Test;
import org.matsim.pt2matsim.config.OsmConverterConfigGroup;
import org.matsim.pt2matsim.osm.lib.OsmData;
import org.matsim.pt2matsim.osm.lib.OsmDataImpl;
import org.matsim.pt2matsim.osm.lib.OsmFileReader;
import org.matsim.pt2matsim.tools.NetworkTools;

/**
 * @author polettif
 */
public class OsmMultimodalNetworkConverterTest {

	private String input = "test/analysis/";

	@Test
	public void convert() throws Exception {
		OsmConverterConfigGroup osmConfig = OsmConverterConfigGroup.createDefaultConfig();
		osmConfig.setOutputCoordinateSystem("EPSG:2032");
		osmConfig.setOsmFile(input + "osm/addison.osm");
		osmConfig.setOutputNetworkFile(input + "network/addisonAttributes.xml");
		osmConfig.setMaxLinkLength(20);

		OsmData osm = new OsmDataImpl();
		new OsmFileReader(osm).readFile(osmConfig.getOsmFile());

		OsmMultimodalNetworkConverterAttr converter = new OsmMultimodalNetworkConverterAttr(osm);
		converter.convert(osmConfig);
		NetworkTools.writeNetwork(converter.getNetwork(), osmConfig.getOutputNetworkFile());
	}

}