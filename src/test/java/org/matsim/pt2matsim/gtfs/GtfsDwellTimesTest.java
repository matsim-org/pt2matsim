package org.matsim.pt2matsim.gtfs;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.matsim.api.core.v01.Id;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.utils.TransitScheduleValidator;
import static org.junit.jupiter.api.Assertions.*;

class GtfsDwellTimesTest {
    @TempDir Path feed;

    @Test void preservesDwellAndDepartureOriginWithZeroTerminalDeparture() throws Exception {
        Files.writeString(feed.resolve("agency.txt"), "agency_id,agency_name,agency_url,agency_timezone\n"
                + "A,Test,https://example.org,Europe/Zurich\n");
        Files.writeString(feed.resolve("stops.txt"), "stop_id,stop_name,stop_lat,stop_lon\n"
                + "a,First,47.0,8.0\nb,Middle,47.001,8.001\nc,Last,47.002,8.002\n");
        Files.writeString(feed.resolve("routes.txt"), "route_id,agency_id,route_short_name,route_long_name,route_type\n"
                + "L,A,L,Test line,3\n");
        Files.writeString(feed.resolve("trips.txt"), "route_id,service_id,trip_id\nL,D,T\n");
        Files.writeString(feed.resolve("calendar.txt"), "service_id,monday,tuesday,wednesday,thursday,friday,saturday,sunday,start_date,end_date\n"
                + "D,1,1,1,1,1,1,1,20240101,20241231\n");
        Files.writeString(feed.resolve("stop_times.txt"), "trip_id,arrival_time,departure_time,stop_id,stop_sequence\n"
                + "T,07:55:00,08:00:00,a,1\nT,08:10:00,08:12:00,b,2\nT,08:20:00,08:25:00,c,3\n");
        var converter = new GtfsConverter(new GtfsFeedImpl(feed.toString()));
        var schedule = converter.convert("20240223", "EPSG:2056");
        var route = schedule.getTransitLines().get(Id.create("L", TransitLine.class)).getRoutes().values().iterator().next();
        assertEquals(8 * 3600, route.getDepartures().values().iterator().next().getDepartureTime());
        assertEquals(0, route.getStops().get(0).getArrivalOffset().seconds());
        assertEquals(0, route.getStops().get(0).getDepartureOffset().seconds());
        assertEquals(600, route.getStops().get(1).getArrivalOffset().seconds());
        assertEquals(720, route.getStops().get(1).getDepartureOffset().seconds());
        assertEquals(1200, route.getStops().get(2).getArrivalOffset().seconds());
        assertEquals(0, route.getStops().get(2).getDepartureOffset().seconds());
        assertTrue(TransitScheduleValidator.validateOffsets(schedule).isValid());
    }
}
