package org.matsim.pt2matsim.tools;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.pt2matsim.gtfs.lib.GtfsShape;
import org.matsim.pt2matsim.lib.RouteShape;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * @author polettif
 */
public class ShapeToolsTest {

	public static final double offset = 1.2;
	private static final double d = 0.0001;
	private RouteShape shapeA1;
	private RouteShape shapeA2;
	private RouteShape shapeB;

	public static Map<Id<RouteShape>, RouteShape> initShapes() {
		Map<Id<RouteShape>, RouteShape> shapes = new HashMap<>();

		RouteShape shapeA1 = new GtfsShape("A1");
		shapeA1.addPoint(NetworkToolsTest.coordE, 1);
		shapeA1.addPoint(NetworkToolsTest.coordD, 2);
		shapeA1.addPoint(NetworkToolsTest.coordA, 3);
		shapeA1.addPoint(NetworkToolsTest.coordX, 4);
		shapeA1.addPoint(NetworkToolsTest.coordB, 5);
		shapeA1.addPoint(NetworkToolsTest.coordI, 6);
		shapes.put(shapeA1.getId(), shapeA1);

		RouteShape shapeA2 = new GtfsShape("A2");
		shapeA2.addPoint(offset(NetworkToolsTest.coordE), 6);
		shapeA2.addPoint(offset(NetworkToolsTest.coordD), 5);
		shapeA2.addPoint(offset(NetworkToolsTest.coordA), 4);
		shapeA2.addPoint(offset(NetworkToolsTest.coordX), 3);
		shapeA2.addPoint(offset(NetworkToolsTest.coordB), 2);
		shapeA2.addPoint(offset(NetworkToolsTest.coordI), 1);
		shapes.put(shapeA2.getId(), shapeA2);

		RouteShape shapeB = new GtfsShape("B");
		shapeB.addPoint(NetworkToolsTest.coordE, 1);
		shapeB.addPoint(NetworkToolsTest.coordW, 2);
		shapeB.addPoint(NetworkToolsTest.coordD, 3);
		shapeB.addPoint(NetworkToolsTest.coordC, 4);
		shapeB.addPoint(NetworkToolsTest.coordX, 5);
		shapeB.addPoint(NetworkToolsTest.coordA, 6);
		shapeB.addPoint(NetworkToolsTest.coordH, 7);
		shapeB.addPoint(NetworkToolsTest.coordZ, 8);
		shapeB.addPoint(NetworkToolsTest.coordI, 9);
		shapes.put(shapeB.getId(), shapeB);

		return shapes;
	}

	private static Coord offset(Coord c) {
		return new Coord(c.getX() + offset, c.getY() + offset);
	}

	@Before
	public void prepare() {
		Map<Id<RouteShape>, RouteShape> shapes = initShapes();
		this.shapeA1 = shapes.get(Id.create("A1", RouteShape.class));
		this.shapeA2 = shapes.get(Id.create("A2", RouteShape.class));
		this.shapeB = shapes.get(Id.create("B", RouteShape.class));
	}

	@Test
	public void calcMinDistanceToShape() throws Exception {
		Assert.assertEquals(0, ShapeTools.calcMinDistanceToShape(NetworkToolsTest.coordX, shapeB), d);
		Assert.assertEquals(5, ShapeTools.calcMinDistanceToShape(new Coord(-5, -5), shapeB), d);

		Coord bx = CoordTools.calcNewPoint(NetworkToolsTest.coordX, CoordTools.getAzimuth(NetworkToolsTest.coordX, NetworkToolsTest.coordB), CoordUtils.calcEuclideanDistance(NetworkToolsTest.coordX, NetworkToolsTest.coordB) / 2);
		Assert.assertEquals(0, ShapeTools.calcMinDistanceToShape(bx, shapeA1), d);
	}


	@Test
	public void getNodesWithinBuffer() throws Exception {
		Collection<Node> nodes = ShapeTools.getNodesWithinBuffer(NetworkToolsTest.initNetwork(), shapeB, 1.0);
		Assert.assertEquals(9, nodes.size());
	}

	@Test
	public void getShapeLength() throws Exception {
		double lengthA1 = ShapeTools.getShapeLength(shapeA1);
		double lengthA2 = ShapeTools.getShapeLength(shapeA2);
		Assert.assertEquals(lengthA1, lengthA2, d);
	}

}