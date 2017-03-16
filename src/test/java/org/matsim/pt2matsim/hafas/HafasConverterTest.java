package org.matsim.pt2matsim.hafas;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt2matsim.tools.ScheduleTools;
import org.matsim.vehicles.VehicleUtils;
import org.matsim.vehicles.Vehicles;

import java.io.IOException;

/**
 * @author polettif
 */
public class HafasConverterTest {

	private final String input = "test/input/PT2MATSimTest/";
	private final String output = "test/output/PT2MATSimTest/";
	private TransitSchedule schedule;
	private Vehicles vehicles;

	@Before
	public void convert() throws IOException {
		this.schedule = ScheduleTools.createSchedule();
		this.vehicles = VehicleUtils.createVehiclesContainer();
		String hafasFolder = input + "BrienzRothornBahn-HAFAS/";
		String cs = "WGS84";
		CoordinateTransformation ct = TransformationFactory.getCoordinateTransformation("WGS84", cs);

		HafasConverter.run(hafasFolder, schedule, ct, vehicles);
	}

	@Test
	public void transitRoutes() {
		Assert.assertEquals(1, schedule.getTransitLines().size());

		int nRoutes = 0;
		for(TransitLine tl : schedule.getTransitLines().values()) {
			Assert.assertEquals("BRB", tl.getId().toString());
			for(TransitRoute tr : tl.getRoutes().values()) {
				nRoutes++;
			}
		}
		Assert.assertEquals(2, nRoutes);
	}

	@Test
	public void nStops() {
		Assert.assertEquals(3, schedule.getFacilities().size());
	}

}