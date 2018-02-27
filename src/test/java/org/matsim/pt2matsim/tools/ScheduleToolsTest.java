
package org.matsim.pt2matsim.tools;

import org.junit.Assert;
import org.junit.Test;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.core.population.routes.NetworkRoute;
import org.matsim.pt.transitSchedule.api.*;
import org.matsim.pt.utils.TransitScheduleValidator;
import org.matsim.pt2matsim.mapping.pseudoRouter.LinkSequence;
import org.matsim.pt2matsim.tools.lib.RouteShape;

import java.util.LinkedList;
import java.util.List;

/**
 * @author polettif
 */
public class ScheduleToolsTest {

	public static final Id<TransitLine> LINE_A = Id.create("lineA", TransitLine.class);
	public static final Id<TransitLine> LINE_B = Id.create("lineB", TransitLine.class);
	public static final Id<TransitRoute> ROUTE_A1 = Id.create("routeA1", TransitRoute.class);
	public static final Id<TransitRoute> ROUTE_A2 = Id.create("routeA2", TransitRoute.class);
	public static final Id<TransitRoute> ROUTE_B = Id.create("routeB", TransitRoute.class);

	public static TransitSchedule initSchedule() {
		TransitSchedule transitSchedule = ScheduleTools.createSchedule();
		TransitScheduleFactory fac = transitSchedule.getFactory();

		// create StopFacilities
		TransitStopFacility stop1ED = fac.createTransitStopFacility(Id.create("stop1.link:ED", TransitStopFacility.class), new Coord(-19, 20), false);
		TransitStopFacility stop1EW = fac.createTransitStopFacility(Id.create("stop1.link:EW", TransitStopFacility.class), new Coord(-19, 20), false);
		TransitStopFacility stop1DE = fac.createTransitStopFacility(Id.create("stop1.link:DE", TransitStopFacility.class), new Coord(-19, 20), false);
		TransitStopFacility stop3AX = fac.createTransitStopFacility(Id.create("stop3.link:AX", TransitStopFacility.class), new Coord(9, 4), false);
		TransitStopFacility stop3XA = fac.createTransitStopFacility(Id.create("stop3.link:XA", TransitStopFacility.class), new Coord(9, 4), false);
		TransitStopFacility stop2AD = fac.createTransitStopFacility(Id.create("stop2.link:AD", TransitStopFacility.class), new Coord(0, 10), false);
		TransitStopFacility stop2DA = fac.createTransitStopFacility(Id.create("stop2.link:DA", TransitStopFacility.class), new Coord(0, 10), false);
		TransitStopFacility stop4IB = fac.createTransitStopFacility(Id.create("stop4.link:IB", TransitStopFacility.class), new Coord(25, -18), false);
		TransitStopFacility stop4BI = fac.createTransitStopFacility(Id.create("stop4.link:BI", TransitStopFacility.class), new Coord(25, -18), false);
		stop1ED.setLinkId(Id.createLinkId("ED"));
		stop2DA.setLinkId(Id.createLinkId("DA"));
		stop3AX.setLinkId(Id.createLinkId("AX"));
		stop4BI.setLinkId(Id.createLinkId("BI"));
		stop1DE.setLinkId(Id.createLinkId("DE"));
		stop2AD.setLinkId(Id.createLinkId("AD"));
		stop3XA.setLinkId(Id.createLinkId("XA"));
		stop4IB.setLinkId(Id.createLinkId("IB"));

		TransitStopFacility stop5AH = fac.createTransitStopFacility(Id.create("stop5.link:AH", TransitStopFacility.class), new Coord(-1, -5), false);
		TransitStopFacility stop6ZI = fac.createTransitStopFacility(Id.create("stop6.link:ZI", TransitStopFacility.class), new Coord(15, -25), false);
		stop1EW.setLinkId(Id.createLinkId("EW"));
		stop5AH.setLinkId(Id.createLinkId("AH"));
		stop6ZI.setLinkId(Id.createLinkId("ZI"));

		// add to schedule
		transitSchedule.addStopFacility(stop1ED);
		transitSchedule.addStopFacility(stop2DA);
		transitSchedule.addStopFacility(stop3AX);
		transitSchedule.addStopFacility(stop4BI);
		transitSchedule.addStopFacility(stop1DE);
		transitSchedule.addStopFacility(stop2AD);
		transitSchedule.addStopFacility(stop3XA);
		transitSchedule.addStopFacility(stop4IB);
		transitSchedule.addStopFacility(stop1EW);
		transitSchedule.addStopFacility(stop5AH);
		transitSchedule.addStopFacility(stop6ZI);


		// create lines
		TransitLine lineA = fac.createTransitLine(LINE_A);
		transitSchedule.addTransitLine(lineA);
		TransitLine lineB = fac.createTransitLine(LINE_B);
		transitSchedule.addTransitLine(lineB);

		// route A1
		Id<Link> a1start = Id.createLinkId("ED");
		List<Id<Link>> a1route = new LinkedList<>();
		a1route.add(Id.createLinkId("DA"));
		a1route.add(Id.createLinkId("AX"));
		a1route.add(Id.createLinkId("XB"));
		Id<Link> a1end = Id.createLinkId("BI");
		NetworkRoute networkRouteA1 = new LinkSequence(a1start, a1route, a1end);
		List<TransitRouteStop> a1stops = new LinkedList<>();
		a1stops.add(fac.createTransitRouteStop(stop1ED, 0.0, 0.0));
		a1stops.add(fac.createTransitRouteStop(stop2DA, 20.0, 20.0));
		a1stops.add(fac.createTransitRouteStop(stop3AX, 40.0, 40.0));
		a1stops.add(fac.createTransitRouteStop(stop4BI, 80.0, 80.0));
		TransitRoute routeA1 = fac.createTransitRoute(ROUTE_A1, networkRouteA1, a1stops, "bus");
		routeA1.addDeparture(fac.createDeparture(Id.create("a1_1", Departure.class), 8 * 3600));
		routeA1.addDeparture(fac.createDeparture(Id.create("a1_2", Departure.class), 11 * 3600));
		routeA1.addDeparture(fac.createDeparture(Id.create("a1_3", Departure.class), 14 * 3600));
		ScheduleTools.setShapeId(routeA1, Id.create("A1", RouteShape.class));
		lineA.addRoute(routeA1);

		// route A2
		Id<Link> a2start = Id.createLinkId("IB");
		List<Id<Link>> a2route = new LinkedList<>();
		a2route.add(Id.createLinkId("BX"));
		a2route.add(Id.createLinkId("XA"));
		a2route.add(Id.createLinkId("AD"));
		Id<Link> a2end = Id.createLinkId("DE");
		NetworkRoute networkRouteA2 = new LinkSequence(a2start, a2route, a2end);
		List<TransitRouteStop> a2stops = new LinkedList<>();
		a2stops.add(fac.createTransitRouteStop(stop4IB, 0.0, 0.0));
		a2stops.add(fac.createTransitRouteStop(stop3XA, 40.0, 40.0));
		a2stops.add(fac.createTransitRouteStop(stop2AD, 60.0, 60.0));
		a2stops.add(fac.createTransitRouteStop(stop1DE, 80.0, 80.0));
		TransitRoute routeA2 = fac.createTransitRoute(ROUTE_A2, networkRouteA2, a2stops, "bus");
		routeA2.addDeparture(fac.createDeparture(Id.create("a2_1", Departure.class), 9 * 3600));
		routeA2.addDeparture(fac.createDeparture(Id.create("a2_2", Departure.class), 12 * 3600));
		routeA2.addDeparture(fac.createDeparture(Id.create("a2_3", Departure.class), 15 * 3600));
		ScheduleTools.setShapeId(routeA2, Id.create("A2", RouteShape.class));
		lineA.addRoute(routeA2);

		// routeB
		Id<Link> bStart = Id.createLinkId("EW");
		List<Id<Link>> bRoute = new LinkedList<>();
		bRoute.add(Id.createLinkId("WD"));
		bRoute.add(Id.createLinkId("DC"));
		bRoute.add(Id.createLinkId("CX"));
		bRoute.add(Id.createLinkId("XA"));
		bRoute.add(Id.createLinkId("AH"));
		bRoute.add(Id.createLinkId("HZ"));
		bRoute.add(Id.createLinkId("ZI"));
		Id<Link> bEend = Id.createLinkId("IB");
		NetworkRoute networkRouteB = new LinkSequence(bStart, bRoute, bEend);
		List<TransitRouteStop> bStops = new LinkedList<>();
		bStops.add(fac.createTransitRouteStop(stop1EW, 0.0, 0.0));
		bStops.add(fac.createTransitRouteStop(stop3XA, 80.0, 80.0));
		bStops.add(fac.createTransitRouteStop(stop5AH, 100.0, 100.0));
		bStops.add(fac.createTransitRouteStop(stop6ZI, 140.0, 140.0));
		bStops.add(fac.createTransitRouteStop(stop4IB, 160.0, 160.0));
		TransitRoute routeB = fac.createTransitRoute(ROUTE_B, networkRouteB, bStops, "bus");
		routeB.addDeparture(fac.createDeparture(Id.create("b_1", Departure.class), 7 * 3600));
		routeB.addDeparture(fac.createDeparture(Id.create("b_2", Departure.class), 10 * 3600));
		routeB.addDeparture(fac.createDeparture(Id.create("b_3", Departure.class), 13 * 3600));
		routeB.addDeparture(fac.createDeparture(Id.create("b_4", Departure.class), 16 * 3600));
		ScheduleTools.setShapeId(routeB, Id.create("B", RouteShape.class));
		lineB.addRoute(routeB);

		return transitSchedule;
	}

	@Test
	public void validateTestSchedule() {
		Assert.assertTrue(TransitScheduleValidator.validateAll(ScheduleToolsTest.initSchedule(), NetworkToolsTest.initNetwork()).isValid());
	}

	@Test
	public void mergeSchedules() {
		TransitSchedule testSchedule = initSchedule();
		ScheduleTools.mergeSchedules(testSchedule, initSchedule());


		int nRoutesTest=0, nRoutesInit=0;
		for(TransitLine l : testSchedule.getTransitLines().values()) { nRoutesTest += l.getRoutes().size(); }
		for(TransitLine l : initSchedule().getTransitLines().values()) { nRoutesInit += l.getRoutes().size(); }

		Assert.assertEquals(testSchedule.getTransitLines().size(), initSchedule().getTransitLines().size());
		Assert.assertEquals(nRoutesInit, nRoutesTest);
		Assert.assertEquals(testSchedule.getFacilities().size(), initSchedule().getFacilities().size());
	}

	@Test
	public void mergeSchedulesOffset() {
		TransitSchedule baseSchedule = initSchedule();
		TransitSchedule testSchedule = initSchedule();
		ScheduleTools.mergeSchedules(testSchedule, baseSchedule, 24*3600, 60*3600);

		int nRoutesTest=0, nRoutesInit=0, nDeparturesTest=0, nDeparturesInit=0;
		for(TransitLine l : testSchedule.getTransitLines().values()) {
			for(TransitRoute tr : l.getRoutes().values()) {
				nRoutesTest++;
				nDeparturesTest += tr.getDepartures().size();
			}
		}
		for(TransitLine l : initSchedule().getTransitLines().values()) {
			for(TransitRoute tr : l.getRoutes().values()) {
				nRoutesInit++;
				nDeparturesInit += tr.getDepartures().size();
			}
		}

		Assert.assertEquals(testSchedule.getTransitLines().size(), initSchedule().getTransitLines().size());
		Assert.assertEquals(nRoutesInit, nRoutesTest);
		Assert.assertEquals(testSchedule.getFacilities().size(), initSchedule().getFacilities().size());
		Assert.assertEquals(nDeparturesInit*2, nDeparturesTest);
	}

	@Test
	public void mergeSchedulesOffsetTimeLimit() {
		TransitSchedule baseSchedule = initSchedule();
		TransitSchedule testSchedule = initSchedule();
		ScheduleTools.mergeSchedules(testSchedule, baseSchedule, 24*3600, 24*3600+12.5*3600);

		int nRoutesTest=0, nRoutesInit=0, nDeparturesTest=0, nDeparturesInit=0;
		for(TransitLine l : testSchedule.getTransitLines().values()) {
			for(TransitRoute tr : l.getRoutes().values()) {
				nRoutesTest++;
				nDeparturesTest += tr.getDepartures().size();
			}
		}
		for(TransitLine l : initSchedule().getTransitLines().values()) {
			for(TransitRoute tr : l.getRoutes().values()) {
				nRoutesInit++;
				nDeparturesInit += tr.getDepartures().size();
			}
		}

		Assert.assertEquals(testSchedule.getTransitLines().size(), initSchedule().getTransitLines().size());
		Assert.assertEquals(nRoutesInit, nRoutesTest);
		Assert.assertEquals(testSchedule.getFacilities().size(), initSchedule().getFacilities().size());
		Assert.assertEquals(nDeparturesInit+6, nDeparturesTest);
	}
}