package org.matsim.pt2matsim.gtfs;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * @author polettif
 */
public class GtfsRealFeedTest {

	private GtfsFeed feed;

	@Before
	public void loadAndConvert() {
		feed = new GtfsFeedImpl("test/stib-mivb-gtfs.zip");
	}

	@Test
	public void statistics() {
		Assert.assertEquals(2514, feed.getStops().size());
		Assert.assertEquals(92, feed.getRoutes().size());
		Assert.assertEquals(139, feed.getServices().size());
		Assert.assertEquals(806, feed.getShapes().size());
		Assert.assertFalse(feed.usesFrequencies());
	}

	@Test
	public void convert() {
		GtfsConverter converter = new GtfsConverter(feed);
		converter.convert(GtfsConverter.DAY_WITH_MOST_TRIPS, "EPSG:32631");
	}

}