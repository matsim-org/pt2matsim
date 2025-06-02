package org.matsim.pt2matsim.hafas.lib;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.pt.transitSchedule.api.Departure;
import org.matsim.pt.transitSchedule.api.TransitRouteStop;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;
import org.matsim.pt2matsim.tools.ScheduleTools;
import org.matsim.vehicles.VehicleType;

class FPLANRouteValidSectionTest {

    private FPLANRoute fplanRoute;
    private final static int MATSIM_INITIAL_DELAY = 60;

    /**
     * Train Route: A -> B -> C -> D -> E -> B -> A
     * Sections to be tested:
     * 1. A -> B
     * 2. B -> E
     * 3. E -> A
     * Diagram:
     *      A
     *      |
     *      B
     *     / \
     *    C   E
     *     \ /
     *      D
     */
    @BeforeEach
    public void setUp() {
        fplanRoute = new FPLANRoute("OperatorName", "OperatorCode", "123", 1, 0);
        fplanRoute.setVehicleTypeId(Id.create("Vehicle", VehicleType.class));
        TransitSchedule schedule = ScheduleTools.createSchedule();
        FPLANRoute.setSchedule(schedule);

        fplanRoute.addLocalBitfeldNr(1, "A", "B");
        fplanRoute.addLocalBitfeldNr(2, "B", "E");
        fplanRoute.addLocalBitfeldNr(3, "E", "A");

        fplanRoute.addRouteStop("A", 1, 1);
        fplanRoute.addRouteStop("B", 2, 2);
        fplanRoute.addRouteStop("C", 3, 3);
        fplanRoute.addRouteStop("D", 4, 4);
        fplanRoute.addRouteStop("E", 5, 5);
        fplanRoute.addRouteStop("B", 6, 6);
        fplanRoute.addRouteStop("A", 7, 7);

        for (char c : "ABCDE".toCharArray()) {
            TransitStopFacility stop = schedule.getFactory().createTransitStopFacility(Id.create(String.valueOf(c), TransitStopFacility.class), new Coord(0,0), false);
            schedule.addStopFacility(stop);
        }
    }

    @Test
    public void testAllSectionsValid() {
        List<TransitRouteStop> result = fplanRoute.getTransitRouteStops();
        Departure firstDeparture = fplanRoute.getDepartures().getFirst();

        String stopChain = result.stream().map(s -> s.getStopFacility().getId().toString()).collect(Collectors.joining());
        assertEquals(7, result.size());
        assertEquals("ABCDEBA", stopChain);
        assertEquals(1.0 - MATSIM_INITIAL_DELAY, firstDeparture.getDepartureTime());
    }

    @Test
    public void testOnlySection3Valid() {
        Set<Integer> bitfeldNummern = new HashSet<>();
        bitfeldNummern.add(3);

        List<TransitRouteStop> result = fplanRoute.getTransitRouteStops(bitfeldNummern);
        Departure firstDeparture = fplanRoute.getDepartures().getFirst();

        String stopChain = result.stream().map(s -> s.getStopFacility().getId().toString()).collect(Collectors.joining());
        assertEquals(3, result.size());
        assertEquals("EBA", stopChain);
        assertEquals(5.0 - MATSIM_INITIAL_DELAY, firstDeparture.getDepartureTime());
    }

    @Test
    public void testSections1And2Valid() {
        Set<Integer> bitfeldNummern = new HashSet<>();
        bitfeldNummern.add(1);
        bitfeldNummern.add(2);

        List<TransitRouteStop> result = fplanRoute.getTransitRouteStops(bitfeldNummern);
        Departure firstDeparture = fplanRoute.getDepartures().getFirst();

        String stopChain = result.stream().map(s -> s.getStopFacility().getId().toString()).collect(Collectors.joining());
        assertEquals(5, result.size());
        assertEquals("ABCDE", stopChain);
        assertEquals(1.0 - MATSIM_INITIAL_DELAY, firstDeparture.getDepartureTime());
    }

    @Test
    public void testSections2And3Valid() {
        Set<Integer> bitfeldNummern = new HashSet<>();
        bitfeldNummern.add(2);
        bitfeldNummern.add(3);

        List<TransitRouteStop> result = fplanRoute.getTransitRouteStops(bitfeldNummern);
        Departure firstDeparture = fplanRoute.getDepartures().getFirst();

        String stopChain = result.stream().map(s -> s.getStopFacility().getId().toString()).collect(Collectors.joining());
        assertEquals(6, result.size());
        assertEquals("BCDEBA", stopChain);
        assertEquals(2.0 - MATSIM_INITIAL_DELAY, firstDeparture.getDepartureTime());
    }
}