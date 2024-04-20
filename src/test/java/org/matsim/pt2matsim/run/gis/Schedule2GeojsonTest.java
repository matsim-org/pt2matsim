package org.matsim.pt2matsim.run.gis;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt2matsim.tools.NetworkToolsTest;
import org.matsim.pt2matsim.tools.ScheduleToolsTest;

import java.io.File;

/**
 * @author polettif
 */
class Schedule2GeojsonTest {

	private TransitSchedule schedule;
	private Network network;

	@BeforeEach
	public void prepare() {
		this.schedule = ScheduleToolsTest.initSchedule();
		this.network = NetworkToolsTest.initNetwork();
	}

	@Test
	void run() {
		Schedule2Geojson.run(TransformationFactory.CH1903_LV03_Plus, this.schedule, this.network, "test/schedule.geojson");
		new File("test/schedule.geojson").delete();
	}
}