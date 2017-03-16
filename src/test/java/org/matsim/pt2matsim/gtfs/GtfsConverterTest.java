package org.matsim.pt2matsim.gtfs;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitSchedule;

/**
 * @author polettif
 */
public class GtfsConverterTest {

	private GtfsFeed gtfsFeed;
	private GtfsConverter gtfsConverter;

	@Before
	public void convert() throws Exception {
		String coordinateSystem = "EPSG:2032";

		gtfsFeed = new GtfsFeedImpl("test/Addisoncounty-GTFS/");

		gtfsConverter = new GtfsConverter(gtfsFeed);
		gtfsConverter.convert(GtfsConverter.ALL_SERVICE_IDS, coordinateSystem);
	}

	@Test
	public void numberOfRoutes() {
		TransitSchedule schedule = gtfsConverter.getSchedule();

		int nTransitLines = 0;
		int nTransitRoutes = 0;

		for(TransitLine tl : schedule.getTransitLines().values()) {
			nTransitLines++;
			nTransitRoutes += tl.getRoutes().size();
		}

		Assert.assertEquals(gtfsFeed.getRoutes().size(), nTransitLines);
		Assert.assertEquals(75, nTransitRoutes);
	}

	@Test
	public void testSecondFeed() {
		String coordinateSystem = "WGS84";

		gtfsFeed = new GtfsFeedImpl("test/GrandRiverTransit-GTFS/");

		gtfsConverter = new GtfsConverter(gtfsFeed);
		gtfsConverter.convert(GtfsConverter.ALL_SERVICE_IDS, coordinateSystem);

	}

}