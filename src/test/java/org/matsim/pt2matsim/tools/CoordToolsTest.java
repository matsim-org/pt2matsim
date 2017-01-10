package org.matsim.pt2matsim.tools;

import org.junit.Test;
import org.matsim.api.core.v01.Coord;
import org.matsim.core.utils.geometry.CoordUtils;

import static org.junit.Assert.*;

/**
 * @author polettif
 */
public class CoordToolsTest {

	private final double testDelta = 1/1000.;

	private final Coord coordA = new Coord(0.0, 0.0);
	private final Coord coordB = new Coord(2.0, 0.0);
	private final Coord coordC = new Coord(2.0, 2.0);

	private final Coord coordD = new Coord(0.0, 2.0);
	private final Coord coordE = new Coord(-2.0, 2.0);
	private final Coord coordF = new Coord(-2.0, 0.0);
	private final Coord coordG = new Coord(-2.0, -2.0);
	private final Coord coordH = new Coord(0.0, -2.0);

	private final Coord coordI = new Coord(2.0, -2.0);
	private final Coord coordW = new Coord(-1.0, 3.0);
	private final Coord coordX = new Coord(0.5, 0.5);
	private final Coord coordY = new Coord(1.0, 0.0);
	private final Coord coordP = new Coord(0.7, 0.1);
	private final Coord coordZ = new Coord(1.0, -3.0);

		/*
		     ^
             |
		 W   |
		     |
	 E   ·   D   ·   C
	         |
	 ·   ·   |   ·   ·
	         | X
	 F-------A---Y---B---->
	         |
	 ·   ·   |	 ·   ·
	    	 |
	 G   ·   H   ·   I
             |
	 ·   ·   |   Z   ·
	 */

	@Test
	public void getAzimuth() throws Exception {
		assertEquals(0,   200* CoordTools.getAzimuth(coordA,coordD)/Math.PI, testDelta);
		assertEquals(50,  200* CoordTools.getAzimuth(coordA,coordC)/Math.PI, testDelta);
		assertEquals(100, 200* CoordTools.getAzimuth(coordA,coordB)/Math.PI, testDelta);
		assertEquals(150, 200* CoordTools.getAzimuth(coordA,coordI)/Math.PI, testDelta);
		assertEquals(200, 200* CoordTools.getAzimuth(coordA,coordH)/Math.PI, testDelta);
		assertEquals(250, 200* CoordTools.getAzimuth(coordA,coordG)/Math.PI, testDelta);
		assertEquals(300, 200* CoordTools.getAzimuth(coordA,coordF)/Math.PI, testDelta);
		assertEquals(350, 200* CoordTools.getAzimuth(coordA,coordE)/Math.PI, testDelta);

	}

	@Test
	public void calcNewPoint() {
		Coord newPointD = CoordTools.calcNewPoint(coordA, 0.00 * Math.PI, CoordUtils.calcEuclideanDistance(coordA, coordD));
		assertEquals(newPointD.getX(), coordD.getX(), testDelta);
		assertEquals(newPointD.getY(), coordD.getY(), testDelta);

		Coord newPointX = CoordTools.calcNewPoint(coordA, 0.25 * Math.PI, CoordUtils.calcEuclideanDistance(coordA, coordX));
		assertEquals(newPointX.getX(), coordX.getX(), testDelta);
		assertEquals(newPointX.getY(), coordX.getY(), testDelta);

		Coord newPointB = CoordTools.calcNewPoint(coordA, 0.50*Math.PI, 2);
		assertEquals(newPointB.getX(), coordB.getX(), testDelta);
		assertEquals(newPointB.getY(), coordB.getY(), testDelta);

		Coord newPointG = CoordTools.calcNewPoint(coordA, 1.25*Math.PI, CoordUtils.calcEuclideanDistance(coordA, coordG));
		assertEquals(newPointG.getX(), coordG.getX(), testDelta);
		assertEquals(newPointG.getY(), coordG.getY(), testDelta);

	}

}