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

	private String base = "test/analysis/";
	private String input = "test/input/PT2MATSimTest/";
	private String output = "test/output/";

	@Test
	public void convert() throws Exception {
		// setup config
		OsmConverterConfigGroup osmConfig = OsmConverterConfigGroup.createDefaultConfig();
//		osmConfig.setOutputCoordinateSystem("EPSG:2032");
//		osmConfig.setOsmFile(base + "osm/addison.osm");
//		osmConfig.setOutputNetworkFile(base + "output/addisonAttributes.xml.gz");
		osmConfig.setOutputCoordinateSystem("WGS84");
		osmConfig.setOsmFile(input + "WaterlooCityCentre.osm");
		osmConfig.setOutputNetworkFile(base + "output/WaterlooCityCentre.xml.gz");
		osmConfig.setMaxLinkLength(20);

		// read osm file
		OsmData osm = new OsmDataImpl();
		new OsmFileReader(osm).readFile(osmConfig.getOsmFile());

		// convert
		OsmMultimodalNetworkConverter converter = new OsmMultimodalNetworkConverter(osm);
		converter.convert(osmConfig);

		// write file
		NetworkTools.writeNetwork(converter.getNetwork(), osmConfig.getOutputNetworkFile());
	}

}