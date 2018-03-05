package org.matsim.pt2matsim.tools;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.pt2matsim.gtfs.lib.GtfsShape;
import org.matsim.pt2matsim.tools.lib.RouteShape;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import static org.matsim.pt2matsim.tools.CoordToolsTest.*;

/**
 * @author polettif
 */
public class ShapeToolsTest {

	public static final double offset = 2;
	private static final double d = 0.0001;
	private RouteShape shapeA1;
	private RouteShape shapeA2;
	private RouteShape shapeB;

	public static Map<Id<RouteShape>, RouteShape> initShapes() {
		Map<Id<RouteShape>, RouteShape> shapes = new HashMap<>();

		RouteShape shapeA1 = new GtfsShape("A1");
		shapeA1.addPoint(coordE, 1);
		shapeA1.addPoint(coordD, 2);
		shapeA1.addPoint(coordA, 3);
		shapeA1.addPoint(coordX, 4);
		shapeA1.addPoint(coordB, 5);
		shapeA1.addPoint(coordI, 6);
		shapes.put(shapeA1.getId(), shapeA1);

		RouteShape shapeA2 = new GtfsShape("A2");
		shapeA2.addPoint(offset(coordE), 6);
		shapeA2.addPoint(offset(coordD), 5);
		shapeA2.addPoint(offset(coordA), 4);
		shapeA2.addPoint(offset(coordX), 3);
		shapeA2.addPoint(offset(coordB), 2);
		shapeA2.addPoint(offset(coordI), 1);
		shapes.put(shapeA2.getId(), shapeA2);

		RouteShape shapeB = new GtfsShape("B");
		shapeB.addPoint(coordE, 1);
		shapeB.addPoint(coordW, 2);
		shapeB.addPoint(coordD, 3);
		shapeB.addPoint(coordC, 4);
		shapeB.addPoint(coordX, 5);
		shapeB.addPoint(coordA, 6);
		shapeB.addPoint(coordH, 7);
		shapeB.addPoint(coordZ, 8);
		shapeB.addPoint(coordI, 9);
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
	public void calcMinDistanceToShape() {
		Assert.assertEquals(0, ShapeTools.calcMinDistanceToShape(coordX, shapeB), d);
		Assert.assertEquals(5, ShapeTools.calcMinDistanceToShape(new Coord(2600035, 1200035), shapeB), d);

		Coord bx = CoordTools.calcNewPoint(coordX, CoordTools.getAzimuth(coordX, coordB), CoordUtils.calcEuclideanDistance(coordX, coordB) / 2);
		Assert.assertEquals(0, ShapeTools.calcMinDistanceToShape(bx, shapeA1), d);
	}


	@Test
	public void getNodesWithinBuffer() {
		Collection<Node> nodes = ShapeTools.getNodesWithinBuffer(NetworkToolsTest.initNetwork(), shapeB, 1.0);
		Assert.assertEquals(9, nodes.size());
	}

	@Test
	public void getShapeLength() {
		double lengthA1 = ShapeTools.getShapeLength(shapeA1);
		double lengthA2 = ShapeTools.getShapeLength(shapeA2);
		Assert.assertEquals(lengthA1, lengthA2, d);
	}

}