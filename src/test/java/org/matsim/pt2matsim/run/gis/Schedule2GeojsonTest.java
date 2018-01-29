package org.matsim.pt2matsim.run.gis;

import org.junit.Before;
import org.junit.Test;
import org.matsim.api.core.v01.network.Network;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt2matsim.tools.NetworkToolsTest;
import org.matsim.pt2matsim.tools.ScheduleToolsTest;

import java.io.File;

import static org.junit.Assert.*;

/**
 * @author polettif
 */
public class Schedule2GeojsonTest {

	private TransitSchedule schedule;
	private Network network;

	@Before
	public void prepare() {
		this.schedule = ScheduleToolsTest.initSchedule();
		this.network = NetworkToolsTest.initNetwork();
	}

	@Test
	public void run() {
		new File("test/geojsonSchedule/").mkdir();
		Schedule2Geojson.run(null, "test/geojsonSchedule/", this.schedule, this.network);
		new File("test/geojsonSchedule/transitRoutes.geojson").delete();
		new File("test/geojsonSchedule/stopFacilities.geojson").delete();
		new File("test/geojsonSchedule/refLinks.geojson").delete();
		new File("test/geojsonSchedule/networkNodes.geojson").delete();
		new File("test/geojsonSchedule/networkLinks.geojson").delete();
		new File("test/geojsonSchedule/").delete();
	}
}