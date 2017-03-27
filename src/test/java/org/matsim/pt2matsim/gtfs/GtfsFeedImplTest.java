package org.matsim.pt2matsim.gtfs;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * @author polettif
 */
public class GtfsFeedImplTest {

	private GtfsFeed feed;

	@Before
	public void prepare() {
		feed = new GtfsFeedImpl("test/Addisoncounty-GTFS/");
	}

	@Test
	public void statistics() throws Exception {
		Assert.assertEquals(114, feed.getStops().size());
		Assert.assertEquals(11, feed.getRoutes().size());
		Assert.assertEquals(15, feed.getServices().size());
		Assert.assertEquals(50, feed.getShapes().size());
		Assert.assertFalse(feed.usesFrequencies());
	}

	@Test
	public void transform() {
		feed.transform("EPSG:2032");
	}

}