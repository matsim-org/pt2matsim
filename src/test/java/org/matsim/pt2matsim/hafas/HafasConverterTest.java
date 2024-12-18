package org.matsim.pt2matsim.hafas;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.matsim.api.core.v01.Id;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;
import org.matsim.pt.transitSchedule.api.*;
import org.matsim.pt2matsim.tools.ScheduleTools;
import org.matsim.vehicles.VehicleUtils;
import org.matsim.vehicles.Vehicles;

import java.io.IOException;

/**
 * @author polettif
 */
class HafasConverterTest {

	private TransitSchedule schedule;
	private Vehicles vehicles;

	@BeforeEach
	public void convert() throws IOException {
		this.schedule = ScheduleTools.createSchedule();
		this.vehicles = VehicleUtils.createVehiclesContainer();
		String hafasFolder = "test/BrienzRothornBahn-HAFAS/";
		String cs = "EPSG:2056";
		CoordinateTransformation ct = TransformationFactory.getCoordinateTransformation("WGS84", cs);

		HafasConverter.run(hafasFolder, schedule, ct, vehicles);
	}

	@Test
	void transitRoutes() {
		Assertions.assertEquals(1, schedule.getTransitLines().size());

		int nRoutes = 0;
		for(TransitLine tl : schedule.getTransitLines().values()) {
			Assertions.assertEquals("BRB", tl.getId().toString());
			for(TransitRoute tr : tl.getRoutes().values()) {
				nRoutes++;
			}
		}
		Assertions.assertEquals(2, nRoutes);
	}

	@Test
	void minimalTransferTimes() {
		int nbMinimalTransferTimes = 0;
		MinimalTransferTimes transferTimes = schedule.getMinimalTransferTimes();
		MinimalTransferTimes.MinimalTransferTimesIterator iterator = transferTimes.iterator();
		while (iterator.hasNext()) {
			iterator.next();
			nbMinimalTransferTimes += 1;
		}
		Assertions.assertEquals(3, nbMinimalTransferTimes);

		Assertions.assertEquals(5 * 60.0, transferTimes.get(
				Id.create("8508350", TransitStopFacility.class),
				Id.create("8508350", TransitStopFacility.class)), 0.00001);

		Assertions.assertEquals(6 * 60.0, transferTimes.get(
				Id.create("8508351", TransitStopFacility.class),
				Id.create("8508351", TransitStopFacility.class)), 0.00001);

		Assertions.assertEquals(60 * 60.0, transferTimes.get(
				Id.create("8508350", TransitStopFacility.class),
				Id.create("8508351", TransitStopFacility.class)), 0.00001);
	}

	@Test
	void nStops() {
		Assertions.assertEquals(3, schedule.getFacilities().size());
	}

}