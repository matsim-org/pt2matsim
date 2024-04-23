package org.matsim.pt2matsim.tools;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.matsim.pt2matsim.gtfs.GtfsFeed;
import org.matsim.pt2matsim.gtfs.GtfsFeedImpl;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;

/**
 * @author polettif
 */
class GtfsToolsTest {

	private GtfsFeed gtfsFeed;
	private String output = "test/gtfs-feed-rewrite/";

	@BeforeEach
	public void prepare() {
		gtfsFeed = new GtfsFeedImpl("test/gtfs-feed/");
		new File(output).mkdir();
	}

	@Test
	void writeFiles() throws IOException {
		GtfsTools.writeStopTimes(gtfsFeed.getTrips().values(), output);
		GtfsTools.writeStops(gtfsFeed.getStops().values(), output);
		GtfsTools.writeTrips(gtfsFeed.getTrips().values(), output);
		GtfsTools.writeTransfers(gtfsFeed.getTransfers(), output);
	}

	@Test
	void getDayWithMostServices() {
		Assertions.assertEquals(LocalDate.of(2018, 10, 2), GtfsTools.getDayWithMostServices(gtfsFeed));
	}

	@Test
	void getDayWithMostTrips() {
		Assertions.assertEquals(LocalDate.of(2018, 10, 5), GtfsTools.getDayWithMostTrips(gtfsFeed));
	}

	@AfterEach
	public void clean() {
		new File(output).delete();
	}
}