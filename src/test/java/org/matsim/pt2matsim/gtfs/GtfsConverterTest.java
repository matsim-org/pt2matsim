package org.matsim.pt2matsim.gtfs;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.utils.TransitScheduleValidator;

/**
 * @author polettif
 */
public class GtfsConverterTest {

	private GtfsFeed gtfsFeed;
	private GtfsConverter gtfsConverter;
	private TransitSchedule schedule;
	private String coordSystem = "EPSG:26917";
	private String feedFolder = "test/GrandRiverTransit-GTFS/";

	@Before
	public void convert() {
		gtfsFeed = new GtfsFeedImpl(feedFolder);

		gtfsConverter = new GtfsConverter(gtfsFeed);
		schedule = gtfsConverter.convert(GtfsConverter.ALL_SERVICE_IDS, coordSystem);
	}

	@Test
	public void convertDayWithMostServices() {
		gtfsConverter.convert(GtfsConverter.DAY_WITH_MOST_SERVICES, coordSystem);
	}

	@Test
	public void convertDayWithMostTrips() {
		gtfsConverter.convert(GtfsConverter.DAY_WITH_MOST_TRIPS, coordSystem);
	}

	@Test
	public void numberOfStops() {
		Assert.assertEquals( 	2452, gtfsFeed.getStops().size());
	}

	@Test
	public void numberOfRoutes() {
		int nTransitLines = 0;
		int nTransitRoutes = 0;
		int nDepartures = 0;

		for(TransitLine tl : schedule.getTransitLines().values()) {
			nTransitLines++;
			nTransitRoutes += tl.getRoutes().size();
			for(TransitRoute tr : tl.getRoutes().values()) {
				nDepartures += tr.getDepartures().size();
			}
		}

		Assert.assertEquals("number of transit lines and gtfs routes not equal", gtfsFeed.getRoutes().size(), nTransitLines);
		Assert.assertEquals("number of total departures and gtfs trips not equal", gtfsFeed.getTrips().size(), nDepartures);
	}

	@Test
	public void validUnmappedSchedule() {
		Assert.assertTrue(TransitScheduleValidator.validateAllStopsExist(schedule).isValid());
		Assert.assertTrue(TransitScheduleValidator.validateOffsets(schedule).isValid());
	}


	@Test
	public void testDays() {
		int scheduleServices = gtfsConverter.convert(GtfsConverter.DAY_WITH_MOST_SERVICES, coordSystem).getTransitLines().size();
		int scheduleDay = gtfsConverter.convert("20161027", coordSystem).getTransitLines().size();

		Assert.assertEquals(scheduleServices, scheduleDay);
		Assert.assertEquals(gtfsFeed.getRoutes().size(), 74);
	}

}