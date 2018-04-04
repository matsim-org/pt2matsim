package org.matsim.pt2matsim.hafas;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.matsim.api.core.v01.Id;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;
import org.matsim.pt.transitSchedule.TransitScheduleWriterV2;
import org.matsim.pt.transitSchedule.api.*;
import org.matsim.pt2matsim.tools.ScheduleTools;
import org.matsim.vehicles.VehicleUtils;
import org.matsim.vehicles.Vehicles;

import java.io.IOException;

/**
 * @author polettif
 */
public class HafasConverterTest {

	private TransitSchedule schedule;
	private Vehicles vehicles;

	@Before
	public void convert() throws IOException {
		this.schedule = ScheduleTools.createSchedule();
		this.vehicles = VehicleUtils.createVehiclesContainer();
		String hafasFolder = "test/BrienzRothornBahn-HAFAS/";
		String cs = "EPSG:2056";
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
	public void minimalTransferTimes() {
		int nbMinimalTransferTimes = 0;
		MinimalTransferTimes transferTimes = schedule.getMinimalTransferTimes();
		MinimalTransferTimes.MinimalTransferTimesIterator iterator = transferTimes.iterator();
		while (iterator.hasNext()) {
			iterator.next();
			nbMinimalTransferTimes += 1;
		}
		Assert.assertEquals(3, nbMinimalTransferTimes);

		Assert.assertEquals(5*60.0, transferTimes.get(
				Id.create("8508350", TransitStopFacility.class),
				Id.create("8508350", TransitStopFacility.class)), 0.00001);

		Assert.assertEquals(6*60.0, transferTimes.get(
				Id.create("8508351", TransitStopFacility.class),
				Id.create("8508351", TransitStopFacility.class)), 0.00001);

		Assert.assertEquals(60*60.0, transferTimes.get(
				Id.create("8508350", TransitStopFacility.class),
				Id.create("8508351", TransitStopFacility.class)), 0.00001);
	}

	@Test
	public void nStops() {
		Assert.assertEquals(3, schedule.getFacilities().size());
	}

}