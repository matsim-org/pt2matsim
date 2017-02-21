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

	private GtfsConverter gtfsConverter;

	@Before
	public void convert() throws Exception {
		String coordinateSystem = "EPSG:2032";

		GtfsFeed gtfsFeed = new GtfsFeedImpl("test/analysis/addisoncounty-vt-us-gtfs/");

		gtfsConverter = new GtfsConverter(gtfsFeed);
		gtfsConverter.convert(GtfsConverter.ALL_SERVICE_IDS, coordinateSystem);
	}

	@Test
	public void numberOfTransitRoutes() {
		TransitSchedule schedule = gtfsConverter.getSchedule();

		int nTransitLines = 0;
		int nTransitRoutes = 0;

		for(TransitLine tl : schedule.getTransitLines().values()) {
			nTransitLines++;
			nTransitRoutes += tl.getRoutes().size();
		}

		Assert.assertEquals(11, nTransitLines);
		Assert.assertEquals(75, nTransitRoutes);
	}

}