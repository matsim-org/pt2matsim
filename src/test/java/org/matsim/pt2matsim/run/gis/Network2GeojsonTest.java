package org.matsim.pt2matsim.run.gis;

import org.junit.Before;
import org.junit.Test;
import org.matsim.api.core.v01.network.Network;
import org.matsim.pt2matsim.tools.NetworkToolsTest;

import java.io.File;

import static org.junit.Assert.*;

/**
 * @author polettif
 */
public class Network2GeojsonTest {

	private Network network;

	@Before
	public void prepare() {
		this.network = NetworkToolsTest.initNetwork();
	}

	@Test
	public void run() {
		Network2Geojson.run(network, "test/nodes.geojson", "test/links.geojson", null);
		new File("test/nodes.geojson").delete();
		new File("test/links.geojson").delete();
	}

}