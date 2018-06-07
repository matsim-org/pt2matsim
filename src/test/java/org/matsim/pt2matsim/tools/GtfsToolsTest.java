package org.matsim.pt2matsim.tools;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.matsim.pt2matsim.gtfs.GtfsFeed;
import org.matsim.pt2matsim.gtfs.GtfsFeedImpl;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;

/**
 * @author polettif
 */
public class GtfsToolsTest {

	private GtfsFeed gtfsFeed;
	private String output = "test/gtfs-feed-rewrite/";

	@Before
	public void prepare() {
		gtfsFeed = new GtfsFeedImpl("test/gtfs-feed/");
		new File(output).mkdir();
	}

	@Test
	public void writeFiles() throws IOException {
		GtfsTools.writeStopTimes(gtfsFeed.getTrips().values(), output);
		GtfsTools.writeStops(gtfsFeed.getStops().values(), output);
		GtfsTools.writeTrips(gtfsFeed.getTrips().values(), output);
		GtfsTools.writeTransfers(gtfsFeed.getTransfers(), output);
	}

	@Test
	public void getDayWithMostServices() {
		Assert.assertEquals(LocalDate.of(2018, 10, 2), GtfsTools.getDayWithMostServices(gtfsFeed));
	}

	@Test
	public void getDayWithMostTrips() {
		Assert.assertEquals(LocalDate.of(2018, 10, 5), GtfsTools.getDayWithMostTrips(gtfsFeed));
	}

	@After
	public void clean() {
		new File(output).delete();
	}
}