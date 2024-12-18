package org.matsim.pt2matsim.gtfs;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;
import org.matsim.pt2matsim.gtfs.lib.Service;
import org.matsim.pt2matsim.gtfs.lib.Stop;
import org.matsim.pt2matsim.gtfs.lib.StopImpl;
import org.matsim.pt2matsim.tools.GtfsTools;
import org.matsim.pt2matsim.tools.ShapeToolsTest;
import org.matsim.pt2matsim.tools.lib.RouteShape;

import java.io.File;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * @author polettif
 */
class GtfsFeedImplTest {

	private GtfsFeed feed;

	@BeforeEach
	public void prepare() {
		feed = new GtfsFeedImpl("test/gtfs-feed/");
		Assertions.assertEquals(TransformationFactory.WGS84, feed.getCurrentCoordSystem());
	}

	@Test
	void compareShapes() {
		feed.transform(TransformationFactory.CH1903_LV03_Plus);
		for(Map.Entry<Id<RouteShape>, RouteShape> entry : ShapeToolsTest.initShapes().entrySet()) {
			RouteShape feedShape = feed.getShapes().get(entry.getKey());
			for(Map.Entry<Integer, Coord> coordEntry : entry.getValue().getCoordsSorted().entrySet()) {
				Assertions.assertEquals(coordEntry.getValue(), feedShape.getCoordsSorted().get(coordEntry.getKey()));
			}
		}
	}

	@Test
	void statistics() {
		Assertions.assertEquals(6, feed.getStops().size());
		Assertions.assertEquals(3, feed.getRoutes().size());
		Assertions.assertEquals(4, feed.getServices().size());
		Assertions.assertEquals(3, feed.getShapes().size());
		Assertions.assertEquals(6, feed.getTrips().size());
		Assertions.assertEquals(6, feed.getTransfers().size());
	}

	@Test
	void stopsAndCoordsEqualAfterRetransform() {
		Map<String, Coord> stopCoords1 = cloneFeedStopCoords(feed);

		// transform
		feed.transform(TransformationFactory.CH1903_LV03_Plus);
		Map<String, Coord> stopCoords2 = cloneFeedStopCoords(feed);

		// check if coords have been transformed
		testCoordsEqual(stopCoords1, stopCoords2, false);

		// check if StopImpl is still equal for one example stop
		Stop testStop = feed.getStops().values().stream().findFirst().
				<Stop>map(s -> new StopImpl(s.getId(), s.getName(), s.getLon(), s.getLat(), s.getLocationType(), s.getParentStationId())).
				orElse(null);
				Assertions.assertNotNull(testStop);
				Assertions.assertEquals(testStop, feed.getStops().get(testStop.getId()));
				Assertions.assertEquals(TransformationFactory.CH1903_LV03_Plus, feed.getCurrentCoordSystem());

		// retransform
		feed.transform(TransformationFactory.WGS84);
		Map<String, Coord> stopCoords3 = cloneFeedStopCoords(feed);
		// check if coords are equal again
		testCoordsEqual(stopCoords1, stopCoords3, true);
	}

	private Map<String, Coord> cloneFeedStopCoords(GtfsFeed feed) {
		Map<String, Coord> cloned = new HashMap<>();
		for(Stop stop : feed.getStops().values()) {
			cloned.put(stop.getId(), new Coord(stop.getCoord().getX(), stop.getCoord().getY()));
		}
		return cloned;
	}

	private void testCoordsEqual(Map<String, Coord> stopCoordsExpected, Map<String, Coord> stopCoordsActual, boolean expectedBool) {
		double delta = 0.000001;
		for(Map.Entry<String, Coord> entry : stopCoordsActual.entrySet()) {
			Coord orig = stopCoordsExpected.get(entry.getKey());
			Coord compare = entry.getValue();

			if(expectedBool) {
				Assertions.assertEquals(orig.getX(), compare.getX(), delta);
				Assertions.assertEquals(orig.getY(), compare.getY(), delta);
			} else {
				Assertions.assertNotEquals(orig.getX(), compare.getX(), delta);
				Assertions.assertNotEquals(orig.getY(), compare.getY(), delta);
			}
		}
	}

	@Test
	void testServices() {
		Service emptService = feed.getServices().get("EMPT");
		Assertions.assertEquals(1, emptService.getAdditions().size());
		Assertions.assertEquals(1, emptService.getExceptions().size());
		Assertions.assertEquals(LocalDate.of(2018, 10, 2), emptService.getAdditions().first());
		Assertions.assertEquals(LocalDate.of(2018, 10, 1), emptService.getExceptions().first());
		Assertions.assertEquals(LocalDate.of(2018, 10, 7), emptService.getEndDate());

		Set<LocalDate> covered = new TreeSet<>();
		covered.add(LocalDate.of(2018, 10, 2));
		covered.add(LocalDate.of(2018, 10, 3));
		covered.add(LocalDate.of(2018, 10, 4));
		covered.add(LocalDate.of(2018, 10, 6));
		Assertions.assertEquals(covered, emptService.getCoveredDays());
	}

	@Test
	void gtfsShapesGeojson() {
		GtfsTools.writeShapesToGeojson(feed, "test/shapes.geojson");
		new File("test/shapes.geojson").delete();
	}

	@Test
	void missingCalendar() {
		new GtfsFeedImpl("test/gtfs-feed-cal/");
	}

}