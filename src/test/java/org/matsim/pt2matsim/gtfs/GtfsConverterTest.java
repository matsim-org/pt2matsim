package org.matsim.pt2matsim.gtfs;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitSchedule;

/**
 * @author polettif
 */
public class GtfsConverterTest {

	private GtfsFeed gtfsFeed;
	private GtfsConverter gtfsConverter;
	private TransitSchedule scheduleAddison;

	@Before
	public void convert() throws Exception {
		String coordinateSystem = "EPSG:2032";

		gtfsFeed = new GtfsFeedImpl("test/Addisoncounty-GTFS/");

		gtfsConverter = new GtfsConverter(gtfsFeed);
		scheduleAddison = gtfsConverter.convert(GtfsConverter.ALL_SERVICE_IDS, coordinateSystem);
		gtfsConverter.convert(GtfsConverter.DAY_WITH_MOST_SERVICES, coordinateSystem);
		gtfsConverter.convert(GtfsConverter.DAY_WITH_MOST_TRIPS, coordinateSystem);
	}

	@Test
	public void numberOfRoutes() {
		int nTransitLines = 0;
		int nTransitRoutes = 0;
		int nDepartures = 0;

		for(TransitLine tl : scheduleAddison.getTransitLines().values()) {
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
	public void testSecondFeed() {
		String coordinateSystem = "WGS84";

		gtfsFeed = new GtfsFeedImpl("test/GrandRiverTransit-GTFS/");

		GtfsConverter gtfsConverter2 = new GtfsConverter(gtfsFeed);
		int scheduleServices = gtfsConverter2.convert(GtfsConverter.DAY_WITH_MOST_SERVICES, coordinateSystem).getTransitLines().size();
		int scheduleDay = gtfsConverter2.convert("20161027", coordinateSystem).getTransitLines().size();

		Assert.assertEquals(scheduleServices, scheduleDay);
	}

}