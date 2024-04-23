package org.matsim.pt2matsim.gtfs;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;
import org.matsim.pt.transitSchedule.api.*;
import org.matsim.pt.utils.TransitScheduleValidator;
import org.matsim.pt2matsim.tools.ScheduleTools;
import org.matsim.pt2matsim.tools.ScheduleToolsTest;

import java.util.*;

/**
 * @author polettif
 */
class GtfsConverterTest {

	private GtfsFeed gtfsFeed;
	private GtfsConverter gtfsConverter;
	private String coordSystem = TransformationFactory.CH1903_LV03_Plus;
	private TransitSchedule convertedSchedule;

	@BeforeEach
	public void convert() {
		gtfsFeed = new GtfsFeedImpl("test/gtfs-feed/");

		gtfsConverter = new GtfsConverter(gtfsFeed);
		convertedSchedule = gtfsConverter.convert("20181005", coordSystem);
	}

	@Test
	void convertAll() {
		TransitSchedule schedule = gtfsConverter.convert(GtfsConverter.ALL_SERVICE_IDS, coordSystem);
		Assertions.assertTrue(TransitScheduleValidator.validateAllStopsExist(schedule).isValid());
		Assertions.assertTrue(TransitScheduleValidator.validateOffsets(schedule).isValid());
	}

	@Test
	void numberOfStopsAndRoutes() {
		int nTransitRoutes = 0;
		for(TransitLine transitLine : convertedSchedule.getTransitLines().values()) {
			nTransitRoutes += transitLine.getRoutes().size();
		}
		Assertions.assertEquals(6, convertedSchedule.getFacilities().size());
		Assertions.assertEquals(3, convertedSchedule.getTransitLines().size());
		Assertions.assertEquals(3, nTransitRoutes);
	}

	@Test
	void departuresFromFrequencies() {
		TransitSchedule baseSchedule = ScheduleToolsTest.initSchedule();
		for(TransitLine transitLine : convertedSchedule.getTransitLines().values()) {
			for(TransitRoute transitRoute : transitLine.getRoutes().values()) {
				TransitRoute expectedRoute = baseSchedule.getTransitLines().get(transitLine.getId()).getRoutes().get(transitRoute.getId());
				Collection<Departure> expected = expectedRoute.getDepartures().values();
				Set<Double> expectedDepTimes = new HashSet<>();
				for(Departure departure : expected) {
					expectedDepTimes.add(departure.getDepartureTime());
				}

				Collection<Departure> actual = transitRoute.getDepartures().values();
				Set<Double> actualDepTimes = new HashSet<>();
				for(Departure departure : actual) {
					actualDepTimes.add(departure.getDepartureTime());
				}

				Assertions.assertEquals(expectedDepTimes, actualDepTimes);
			}
		}
	}

	@Test
	void offsets() {
		TransitSchedule baseSchedule = ScheduleToolsTest.initSchedule();
		for(TransitLine transitLine : convertedSchedule.getTransitLines().values()) {
			for(TransitRoute transitRoute : transitLine.getRoutes().values()) {
				TransitRoute expectedRoute = baseSchedule.getTransitLines().get(transitLine.getId()).getRoutes().get(transitRoute.getId());
				Assertions.assertEquals(expectedRoute.getStops().size(), transitRoute.getStops().size());
				for(int i = 0; i < transitRoute.getStops().size() - 1; i++) {
					if(i > 0) {
						Assertions.assertEquals(expectedRoute.getStops().get(i).getArrivalOffset().seconds(),
								transitRoute.getStops().get(i).getArrivalOffset().seconds(), 0.1);
					}
					if(i < transitRoute.getStops().size() - 2) {
						Assertions.assertEquals(expectedRoute.getStops().get(i).getDepartureOffset().seconds(),
								transitRoute.getStops().get(i).getDepartureOffset().seconds(), 0.1);
					}
				}
			}
		}
	}

	@Test
	void stops() {
		TransitSchedule baseSchedule = ScheduleToolsTest.initUnmappedSchedule();
		for(TransitStopFacility actualStopFac : convertedSchedule.getFacilities().values()) {
			TransitStopFacility expectedStopFac = baseSchedule.getFacilities().get(actualStopFac.getId());
			Assertions.assertEquals(expectedStopFac.getCoord(), actualStopFac.getCoord());
			Assertions.assertEquals(expectedStopFac.getName(), actualStopFac.getName());
		}
	}

	@Test
	void combineRoutes() {
		TransitSchedule test = ScheduleTools.createSchedule();
		TransitScheduleFactory f = test.getFactory();
		Id<TransitLine> lineId = Id.create("L", TransitLine.class);
		TransitLine line = f.createTransitLine(lineId);
		test.addTransitLine(line);

		Id<TransitStopFacility> stopId1 = Id.create("s1", TransitStopFacility.class);
		Id<TransitStopFacility> stopId2 = Id.create("s2", TransitStopFacility.class);
		Id<TransitStopFacility> stopId3 = Id.create("s3", TransitStopFacility.class);
		test.addStopFacility(f.createTransitStopFacility(stopId1, new Coord(1, 1), true));
		test.addStopFacility(f.createTransitStopFacility(stopId2, new Coord(2, 2), true));
		test.addStopFacility(f.createTransitStopFacility(stopId3, new Coord(3, 3), true));

		List<TransitRouteStop> routeStops1 = new LinkedList<>();
		List<TransitRouteStop> routeStops2 = new LinkedList<>();
		List<TransitRouteStop> routeStops3 = new LinkedList<>();
		int t = 0;
		for(TransitStopFacility stopFacility : test.getFacilities().values()) {
			routeStops1.add(f.createTransitRouteStop(stopFacility, t * 60, t * 60 + 30));
			routeStops2.add(f.createTransitRouteStop(stopFacility, t * 40, t * 40 + 10));
			routeStops3.add(f.createTransitRouteStop(stopFacility, t * 40, t * 40 + 10));
		}
		TransitRoute route1 = f.createTransitRoute(Id.create("R1", TransitRoute.class), null, routeStops1, "bus");
		route1.addDeparture(f.createDeparture(Id.create("dep1", Departure.class), 0.0));
		TransitRoute route2 = f.createTransitRoute(Id.create("R2", TransitRoute.class), null, routeStops2, "bus");
		route1.addDeparture(f.createDeparture(Id.create("dep2", Departure.class), 0.0));
		TransitRoute route3 = f.createTransitRoute(Id.create("R3", TransitRoute.class), null, routeStops3, "bus");
		route1.addDeparture(f.createDeparture(Id.create("dep3", Departure.class), 4200.0));
		line.addRoute(route1);
		line.addRoute(route2);
		line.addRoute(route3);

		Assertions.assertEquals(3, line.getRoutes().size());
		// only routes with identical stop sequence (1, 2, 3) and departure sequence (2, 3) are combined.
		gtfsConverter.combineTransitRoutes(test);
		Assertions.assertEquals(2, line.getRoutes().size());
	}

	@Test
	void testTransfers() {
		Set<String> expectedTransferTimes = new TreeSet<>();
		MinimalTransferTimes.MinimalTransferTimesIterator iter1 = ScheduleToolsTest.initUnmappedSchedule().getMinimalTransferTimes().iterator();
		while(iter1.hasNext()) {
			iter1.next();
			expectedTransferTimes.add(getTransferTimeTestString(iter1));
		}

		Set<String> actualTransferTime = new TreeSet<>();
		MinimalTransferTimes.MinimalTransferTimesIterator iter2 = convertedSchedule.getMinimalTransferTimes().iterator();
		while(iter2.hasNext()) {
			iter2.next();
			actualTransferTime.add(getTransferTimeTestString(iter2));
		}

		Assertions.assertEquals(expectedTransferTimes, actualTransferTime);
	}

	private String getTransferTimeTestString(MinimalTransferTimes.MinimalTransferTimesIterator iterator) {
		return iterator.getFromStopId().toString() + "-" + iterator.getToStopId().toString() + "-" + iterator.getSeconds();
	}

}