package org.matsim.pt2matsim.run.gis;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;
import org.matsim.pt2matsim.tools.NetworkToolsTest;

import java.io.File;

/**
 * @author polettif
 */
class Network2GeojsonTest {

	private Network network;

	@BeforeEach
	public void prepare() {
		this.network = NetworkToolsTest.initNetwork();
	}

	@Test
	void run() {
		Network2Geojson.run(TransformationFactory.CH1903_LV03_Plus, network, "test/nodes.geojson", "test/links.geojson");
		new File("test/nodes.geojson").delete();
		new File("test/links.geojson").delete();
	}

}