package org.matsim.pt2matsim.gtfs;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.matsim.api.core.v01.Coord;
import org.matsim.pt2matsim.gtfs.lib.Stop;
import org.matsim.pt2matsim.gtfs.lib.StopImpl;
import org.matsim.pt2matsim.tools.GtfsTools;

import java.io.File;
import java.util.*;

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
	public void statistics() {
		Assert.assertEquals(114, feed.getStops().size());
		Assert.assertEquals(11, feed.getRoutes().size());
		Assert.assertEquals(15, feed.getServices().size());
		Assert.assertEquals(50, feed.getShapes().size());
		Assert.assertFalse(feed.usesFrequencies());
	}

	@Test
	public void stopsEqualAfterTransform() {
		Stop testStop = feed.getStops().values().stream().findFirst().
				<Stop>map(s -> new StopImpl(s.getId(), s.getName(), s.getLon(), s.getLat(), s.getLocationType(), s.getParentStationId())).
				orElse(null);
		Assert.assertNotNull(testStop);
		feed.transform("EPSG:2032");
		Assert.assertEquals(testStop, feed.getStops().get(testStop.getId()));
	}

	@Test
	public void coordEqualAfterRetransform() {
		Set<Coord> coords1 = new HashSet<>();
		for(Stop stop : feed.getStops().values()) {
			coords1.add(stop.getCoord());
		}
		feed.transform("EPSG:2032");
		feed.transform("WGS84");
		Set<Coord> coords2 = new HashSet<>();
		for(Stop stop : feed.getStops().values()) {
			coords2.add(new Coord(stop.getCoord().getX(), stop.getCoord().getY()));
		}
		Assert.assertEquals(coords1, coords2);
	}

	@Test
	public void gtfsShapesGeojson() {
		GtfsTools.writeShapesToGeojson(feed, "test/shapes.geojson");
		new File("test/shapes.geojson").delete();
	}


}