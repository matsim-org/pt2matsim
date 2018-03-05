package org.matsim.pt2matsim.gtfs;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.utils.TransitScheduleValidator;
import org.matsim.pt2matsim.tools.GtfsTools;

import java.time.LocalDate;

/**
 * @author polettif
 */
public class GtfsConverterTest {

	private GtfsFeed gtfsFeed;
	private GtfsConverter gtfsConverter;
	private String coordSystem = "Atlantis";
	private TransitSchedule schedule;

	@Before
	public void convert() {
		gtfsFeed = new GtfsFeedImpl("test/gtfs-feed/");

		gtfsConverter = new GtfsConverter(gtfsFeed);
		schedule = gtfsConverter.convert("20181005", coordSystem);
	}

	@Test
	public void convertAll() {
		TransitSchedule schedule = gtfsConverter.convert(GtfsConverter.ALL_SERVICE_IDS, coordSystem);
		Assert.assertTrue(TransitScheduleValidator.validateAllStopsExist(schedule).isValid());
		Assert.assertTrue(TransitScheduleValidator.validateOffsets(schedule).isValid());
	}

	@Test
	public void getDayWithMostServices() {
		Assert.assertEquals(LocalDate.of(2018,10,2), GtfsTools.getDayWithMostServices(gtfsFeed));
	}

	@Test
	public void getDayWithMostTrips() {
		Assert.assertEquals(LocalDate.of(2018, 10, 5), GtfsTools.getDayWithMostTrips(gtfsFeed));
	}

	@Test
	public void numberOfStopsAndRoutes() {
		int nTransitRoutes = 0;
		for(TransitLine transitLine : schedule.getTransitLines().values()) {
			nTransitRoutes += transitLine.getRoutes().size();
		}
		Assert.assertEquals( 	6, schedule.getFacilities().size());
		Assert.assertEquals( 	2, schedule.getTransitLines().size());
		Assert.assertEquals( 	3, nTransitRoutes);
	}

}