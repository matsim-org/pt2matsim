package org.matsim.pt2matsim.gtfs;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.pt2matsim.gtfs.lib.Stop;
import org.matsim.pt2matsim.gtfs.lib.StopImpl;
import org.matsim.pt2matsim.tools.GtfsTools;

import java.io.File;
import java.util.*;

/**
 * @author polettif
 */
public class GtfsFeedImplTest {

	private String gtfsTestFeed = "test/stib-mivb-gtfs.zip";
	private String coordinateSystem = "EPSG:32631";
	private GtfsFeed feed;

	@Before
	public void prepare() {
		feed = new GtfsFeedImpl(gtfsTestFeed);
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
	public void stopsEqualAfterTransform() {
		Stop testStop = feed.getStops().values().stream().findFirst().
				<Stop>map(s -> new StopImpl(s.getId(), s.getName(), s.getLon(), s.getLat(), s.getLocationType(), s.getParentStationId())).
				orElse(null);
		Assert.assertNotNull(testStop);
		feed.transform(coordinateSystem);
		Assert.assertEquals(testStop, feed.getStops().get(testStop.getId()));
	}

	@Test
	public void coordEqualAfterRetransform() {
		Map<String, Coord> stopCoords1 = new HashMap<>();
		for(Stop stop : feed.getStops().values()) {
			stopCoords1.put(stop.getId(), stop.getCoord());
		}
		feed.transform(coordinateSystem);
		feed.transform("WGS84");
		Map<String, Coord> stopCoords2 = new HashMap<>();
		for(Stop stop : feed.getStops().values()) {
			stopCoords2.put(stop.getId(), new Coord(stop.getCoord().getX(), stop.getCoord().getY()));
		}

		for(Map.Entry<String, Coord> entry : stopCoords2.entrySet()) {
			Coord orig = stopCoords1.get(entry.getKey());
			Coord compare = entry.getValue();

			double DELTA = 0.000001;
			Assert.assertEquals(orig.getX(), compare.getX(), DELTA);
			Assert.assertEquals(orig.getY(), compare.getY(), DELTA);
		}
	}

	@Test
	public void gtfsShapesGeojson() {
		GtfsTools.writeShapesToGeojson(feed, "test/shapes.geojson");
		new File("test/shapes.geojson").delete();
	}


}