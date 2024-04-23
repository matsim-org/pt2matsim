package org.matsim.pt2matsim.tools;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.matsim.api.core.v01.Coord;
import org.matsim.core.utils.geometry.CoordUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * @author polettif
 */
public class CoordToolsTest {

	public static final Coord coordA = new Coord(2600040.0, 	1200040.0);
	public static final Coord coordB = new Coord(2600060.0, 	1200040.0);
	public static final Coord coordC = new Coord(2600060.0, 	1200060.0);
	public static final Coord coordD = new Coord(2600040.0, 	1200060.0);
	public static final Coord coordE = new Coord(2600020.0, 	1200060.0);
	public static final Coord coordF = new Coord(2600020.0, 	1200040.0);
	public static final Coord coordG = new Coord(2600020.0, 	1200020.0);
	public static final Coord coordH = new Coord(2600040.0, 	1200030.0);
	public static final Coord coordI = new Coord(2600060.0, 	1200020.0);
	public static final Coord coordW = new Coord(2600030.0, 	1200070.0);
	public static final Coord coordX = new Coord(2600050.0, 	1200045.0);
	public static final Coord coordZ = new Coord(2600050.0, 	1200010.0);

	private final double testDelta = 1/1000.;

	/*
			 ^
             |
		 W   |
		     |
	 E   ·   D   ·   C
	         |
	 ·   ·   |   ·   ·
	         |  X
	 F-------A-------B---->
	         |
	 ·   ·   H	 ·   ·
	    	 |
	 G   ·   |   ·   I
             |
	 ·   ·   |   Z   ·


	    ^
	    |
	    | --> Az+
	    |
	    O

	 */

	@Test
	void getAzimuth() {
		Assertions.assertEquals(0, 200 * CoordTools.getAzimuth(coordA, coordD) / Math.PI, testDelta);
		Assertions.assertEquals(50, 200 * CoordTools.getAzimuth(coordA, coordC) / Math.PI, testDelta);
		Assertions.assertEquals(100, 200 * CoordTools.getAzimuth(coordA, coordB) / Math.PI, testDelta);
		Assertions.assertEquals(150, 200 * CoordTools.getAzimuth(coordA, coordI) / Math.PI, testDelta);
		Assertions.assertEquals(200, 200 * CoordTools.getAzimuth(coordA, coordH) / Math.PI, testDelta);
		Assertions.assertEquals(250, 200 * CoordTools.getAzimuth(coordA, coordG) / Math.PI, testDelta);
		Assertions.assertEquals(300, 200 * CoordTools.getAzimuth(coordA, coordF) / Math.PI, testDelta);
		Assertions.assertEquals(350, 200 * CoordTools.getAzimuth(coordA, coordE) / Math.PI, testDelta);
	}

	@Test
	void getAzimuthDiff() {
		Assertions.assertEquals(100, 200 * CoordTools.getAngleDiff(coordA, coordD, coordC) / Math.PI, testDelta);
		Assertions.assertEquals(150, 200 * CoordTools.getAngleDiff(coordA, coordD, coordB) / Math.PI, testDelta);
		Assertions.assertEquals(-100, 200 * CoordTools.getAngleDiff(coordA, coordB, coordC) / Math.PI, testDelta);
		Assertions.assertEquals(-150, 200 * CoordTools.getAngleDiff(coordA, coordB, coordD) / Math.PI, testDelta);

		Assertions.assertEquals(50, 200 * CoordTools.getAngleDiff(coordH, coordA, coordC) / Math.PI, testDelta);
		Assertions.assertEquals(-50, 200 * CoordTools.getAngleDiff(coordH, coordA, coordE) / Math.PI, testDelta);

		Assertions.assertEquals(0, 200 * CoordTools.getAngleDiff(coordF, coordA, coordB) / Math.PI, testDelta);
		Assertions.assertEquals(200, 200 * CoordTools.getAngleDiff(coordA, coordF, coordB) / Math.PI, testDelta);

		Map<String, Coord> coords = new HashMap<>();
		coords.put("A", coordA); coords.put("B", coordB); coords.put("C", coordC); coords.put("D", coordD);
		coords.put("E", coordE); coords.put("F", coordF); coords.put("G", coordG); coords.put("H", coordH);
		coords.put("I", coordI); coords.put("W", coordW); coords.put("X", coordX); coords.put("Z", coordZ);

		for(Map.Entry<String, Coord> c0 : coords.entrySet()) {
			for(Map.Entry<String, Coord> c1 : coords.entrySet()) {
				for(Map.Entry<String, Coord> c2 : coords.entrySet()) {
					if(!c1.equals(c0) && !c2.equals(c0) && !c1.equals(c2)) {
						double diff = CoordTools.getAngleDiff(c0.getValue(), c1.getValue(), c2.getValue());
						Assertions.assertTrue(diff <= Math.PI);
						Assertions.assertTrue(diff >= -Math.PI);
					}
				}
			}
		}

	}

	@Test
	void calcNewPoint() {
		Coord newPointD = CoordTools.calcNewPoint(coordA, 0.00 * Math.PI, CoordUtils.calcEuclideanDistance(coordA, coordD));
		Assertions.assertEquals(newPointD.getX(), coordD.getX(), testDelta);
		Assertions.assertEquals(newPointD.getY(), coordD.getY(), testDelta);

		Coord newPointX = CoordTools.calcNewPoint(coordA, 0.25 * Math.PI, CoordUtils.calcEuclideanDistance(coordA, coordC));
		Assertions.assertEquals(newPointX.getX(), coordC.getX(), testDelta);
		Assertions.assertEquals(newPointX.getY(), coordC.getY(), testDelta);

		Coord duplicateCoordZ = CoordTools.calcNewPoint(coordA, CoordTools.getAzimuth(coordA, coordZ), CoordUtils.calcEuclideanDistance(coordA, coordZ));
		Assertions.assertEquals(duplicateCoordZ.getX(), coordZ.getX(), testDelta);
		Assertions.assertEquals(duplicateCoordZ.getY(), coordZ.getY(), testDelta);

		Coord newPointB = CoordTools.calcNewPoint(coordA, 0.50 * Math.PI, 20);
		Assertions.assertEquals(newPointB.getX(), coordB.getX(), testDelta);
		Assertions.assertEquals(newPointB.getY(), coordB.getY(), testDelta);

		Coord newPointG = CoordTools.calcNewPoint(coordA, 1.25*Math.PI, CoordUtils.calcEuclideanDistance(coordA, coordG));
		Assertions.assertEquals(newPointG.getX(), coordG.getX(), testDelta);
		Assertions.assertEquals(newPointG.getY(), coordG.getY(), testDelta);
	}

}