package org.matsim.pt2matsim.osm;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.pt2matsim.config.OsmConverterConfigGroup;
import org.matsim.pt2matsim.osm.lib.OsmData;
import org.matsim.pt2matsim.osm.lib.OsmDataImpl;
import org.matsim.pt2matsim.osm.lib.OsmFileReader;

class OsmRailStopPositionTest {

	private static final String OSM = """
			<?xml version='1.0' encoding='UTF-8'?>
			<osm version='0.6'>
			  <node id='1' lat='47.0' lon='8.000'/>
			  <node id='2' lat='47.0' lon='8.001'>
			    <tag k='railway' v='stop'/>
			    <tag k='public_transport' v='stop_position'/>
			    <tag k='train' v='yes'/>
			    <tag k='local_ref' v='3'/>
			    <tag k='name' v='Teststation'/>
			  </node>
			  <node id='3' lat='47.0' lon='8.002'>
			    <tag k='public_transport' v='stop_position'/>
			    <tag k='bus' v='yes'/>
			  </node>
			  <node id='4' lat='47.0' lon='8.003'/>
			  <way id='10'>
			    <nd ref='1'/>
			    <nd ref='2'/>
			    <nd ref='3'/>
			    <nd ref='4'/>
			    <tag k='railway' v='rail'/>
			  </way>
			</osm>
			""";

	@TempDir
	Path tempDir;

	private Network convert(boolean keepRailStopPositions) throws IOException {
		Path osmFile = tempDir.resolve("rail.osm");
		Files.writeString(osmFile, OSM);
		OsmData osm = new OsmDataImpl();
		new OsmFileReader(osm).readFile(osmFile.toString());

		OsmConverterConfigGroup config = OsmConverterConfigGroup.createDefaultConfig();
		config.setOutputCoordinateSystem("EPSG:2056");
		config.setKeepPaths(false);
		config.keepRailStopPositions = keepRailStopPositions;

		OsmMultimodalNetworkConverter converter = new OsmMultimodalNetworkConverter(osm);
		converter.convert(config);
		return converter.getNetwork();
	}

	@Test
	void stopPositionsAreDroppedByDefault() throws IOException {
		Network network = convert(false);
		Assertions.assertNull(network.getNodes().get(Id.createNodeId("2")));
		for (Node node : network.getNodes().values()) {
			Assertions.assertNull(node.getAttributes().getAttribute(OsmConverterConfigGroup.NODE_ATTRIBUTE_RAIL_STOP_POSITION));
		}
	}

	@Test
	void railStopPositionsAreKeptWithAttributes() throws IOException {
		Network network = convert(true);
		Node stop = network.getNodes().get(Id.createNodeId("2"));
		Assertions.assertNotNull(stop);
		Assertions.assertEquals(true, stop.getAttributes().getAttribute(OsmConverterConfigGroup.NODE_ATTRIBUTE_RAIL_STOP_POSITION));
		Assertions.assertEquals("3", stop.getAttributes().getAttribute(OsmConverterConfigGroup.NODE_ATTRIBUTE_LOCAL_REF));
		Assertions.assertEquals("Teststation", stop.getAttributes().getAttribute(OsmConverterConfigGroup.NODE_ATTRIBUTE_NAME));
		Assertions.assertFalse(stop.getInLinks().isEmpty());

		// bus stop positions are not rail stop positions
		Assertions.assertNull(network.getNodes().get(Id.createNodeId("3")));
	}
}
