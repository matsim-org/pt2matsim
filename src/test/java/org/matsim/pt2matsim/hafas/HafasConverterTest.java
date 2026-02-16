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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

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

	@Test
	void durchbindungen() {
		TransitLine transitLine = schedule.getTransitLines().get(Id.create("BRB", TransitLine.class));
		Assertions.assertNotNull(transitLine, "Transit line BRB should exist");

		TransitRoute sourceRoute = transitLine.getRoutes().values().stream()
			.filter(route -> route.getId().toString().startsWith("000001_"))
			.findFirst()
			.orElseThrow(() -> new AssertionError("Source route for trip 000001 not found"));

		TransitRoute targetRoute = transitLine.getRoutes().values().stream()
			.filter(route -> route.getId().toString().startsWith("000002_"))
			.findFirst()
			.orElseThrow(() -> new AssertionError("Target route for trip 000002 not found"));

		List<Departure> sourceDepartures = new ArrayList<>(sourceRoute.getDepartures().values());
		sourceDepartures.sort(Comparator.comparing(dep -> dep.getId().toString()));

		List<Departure> targetDepartures = new ArrayList<>(targetRoute.getDepartures().values());
		targetDepartures.sort(Comparator.comparing(dep -> dep.getId().toString()));

		Assertions.assertFalse(sourceDepartures.isEmpty(), "Source route should have departures");
		Assertions.assertEquals(targetDepartures.size(), sourceDepartures.size(),
			"Source and target routes should have same number of departures for one-to-one chaining");

		for (int i = 0; i < sourceDepartures.size(); i++) {
			Departure sourceDeparture = sourceDepartures.get(i);
			Departure targetDeparture = targetDepartures.get(i);

			Assertions.assertNotNull(sourceDeparture.getChainedDepartures(), "Source departure should define chained departures");
			Assertions.assertEquals(1, sourceDeparture.getChainedDepartures().size(),
				"Each source departure should chain to exactly one target departure in this fixture");

			ChainedDeparture chainedDeparture = sourceDeparture.getChainedDepartures().getFirst();
			Assertions.assertEquals(transitLine.getId(), chainedDeparture.getChainedTransitLineId(), "Chained line id should match BRB line");
			Assertions.assertEquals(targetRoute.getId(), chainedDeparture.getChainedRouteId(), "Chained route id should point to trip 000002 route");
			Assertions.assertEquals(targetDeparture.getId(), chainedDeparture.getChainedDepartureId(),
				"Chained departure id should point to corresponding target departure");
		}
	}

}