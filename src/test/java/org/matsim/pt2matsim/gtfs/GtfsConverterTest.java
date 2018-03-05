package org.matsim.pt2matsim.gtfs;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;
import org.matsim.pt.transitSchedule.api.*;
import org.matsim.pt.utils.TransitScheduleValidator;
import org.matsim.pt2matsim.tools.GtfsTools;
import org.matsim.pt2matsim.tools.ScheduleToolsTest;

import java.time.LocalDate;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * @author polettif
 */
public class GtfsConverterTest {

	private GtfsFeed gtfsFeed;
	private GtfsConverter gtfsConverter;
	private String coordSystem = TransformationFactory.CH1903_LV03_Plus;
	private TransitSchedule convertedSchedule;

	@Before
	public void convert() {
		gtfsFeed = new GtfsFeedImpl("test/gtfs-feed/");

		gtfsConverter = new GtfsConverter(gtfsFeed);
		convertedSchedule = gtfsConverter.convert("20181005", coordSystem);
	}

	@Test
	public void convertAll() {
		TransitSchedule schedule = gtfsConverter.convert(GtfsConverter.ALL_SERVICE_IDS, coordSystem);
		Assert.assertTrue(TransitScheduleValidator.validateAllStopsExist(schedule).isValid());
		Assert.assertTrue(TransitScheduleValidator.validateOffsets(schedule).isValid());
	}

	@Test
	public void getDayWithMostServices() {
		Assert.assertEquals(LocalDate.of(2018, 10, 2), GtfsTools.getDayWithMostServices(gtfsFeed));
	}

	@Test
	public void getDayWithMostTrips() {
		Assert.assertEquals(LocalDate.of(2018, 10, 5), GtfsTools.getDayWithMostTrips(gtfsFeed));
	}

	@Test
	public void numberOfStopsAndRoutes() {
		int nTransitRoutes = 0;
		for(TransitLine transitLine : convertedSchedule.getTransitLines().values()) {
			nTransitRoutes += transitLine.getRoutes().size();
		}
		Assert.assertEquals(6, convertedSchedule.getFacilities().size());
		Assert.assertEquals(2, convertedSchedule.getTransitLines().size());
		Assert.assertEquals(3, nTransitRoutes);
	}

	@Test
	public void departuresFromFrequencies() {
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

				Assert.assertEquals(expectedDepTimes, actualDepTimes);
			}
		}
	}

	@Test
	public void offsets() {
		TransitSchedule baseSchedule = ScheduleToolsTest.initSchedule();
		for(TransitLine transitLine : convertedSchedule.getTransitLines().values()) {
			for(TransitRoute transitRoute : transitLine.getRoutes().values()) {
				TransitRoute expectedRoute = baseSchedule.getTransitLines().get(transitLine.getId()).getRoutes().get(transitRoute.getId());
				Assert.assertEquals(expectedRoute.getStops().size(), transitRoute.getStops().size());
				for(int i = 0; i < transitRoute.getStops().size() - 1; i++) {
					if(i > 0) {
						Assert.assertEquals(expectedRoute.getStops().get(i).getArrivalOffset(), transitRoute.getStops().get(i).getArrivalOffset(), 0.1);
					}
					if(i < transitRoute.getStops().size() - 2) {
						Assert.assertEquals(expectedRoute.getStops().get(i).getDepartureOffset(), transitRoute.getStops().get(i).getDepartureOffset(), 0.1);
					}
				}
			}
		}
	}

	@Test
	public void stops() {
		TransitSchedule baseSchedule = ScheduleToolsTest.initUnmappedSchedule();
		for(TransitStopFacility actualStopFac : convertedSchedule.getFacilities().values()) {
			TransitStopFacility expectedStopFac = baseSchedule.getFacilities().get(actualStopFac.getId());
			Assert.assertEquals(expectedStopFac.getCoord(), actualStopFac.getCoord());
			Assert.assertEquals(expectedStopFac.getName(), actualStopFac.getName());
		}
	}

}