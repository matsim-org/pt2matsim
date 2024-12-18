package org.matsim.pt2matsim.gtfs;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * @author polettif
 */
class GtfsRealFeedTest {

	private GtfsFeed feed;
	private GtfsConverter converter;

	@BeforeEach
	public void loadAndConvert() {
		feed = new GtfsFeedImpl("test/stib-mivb-gtfs.zip");
		converter = new GtfsConverter(feed);
	}

	@Test
	void statistics() {
		Assertions.assertEquals(2514, feed.getStops().size());
		Assertions.assertEquals(92, feed.getRoutes().size());
		Assertions.assertEquals(139, feed.getServices().size());
		Assertions.assertEquals(806, feed.getShapes().size());
	}

	@Test
	void convert() {
		converter.convert(GtfsConverter.DAY_WITH_MOST_TRIPS, "EPSG:32631");
	}

}