package org.matsim.pt2matsim.hafas;

import org.junit.Assert;
import org.junit.Test;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt2matsim.tools.ScheduleTools;
import org.matsim.vehicles.VehicleUtils;
import org.matsim.vehicles.Vehicles;

import java.io.IOException;
import java.util.stream.Collectors;

public class HafasFplanTest {
    @Test
    public void simpleFplanTest() throws IOException {

        String hafasFolder = "test/FPLAN_HAFAS/";

        TransitSchedule schedule = ScheduleTools.createSchedule();
        Vehicles vehicles = VehicleUtils.createVehiclesContainer();
        HafasConverter.run(hafasFolder, schedule, null, vehicles);

        int nbRoutes = schedule.getTransitLines().values().stream().flatMap(l -> l.getRoutes().values().stream()).collect(Collectors.toList()).size();
        Assert.assertEquals(2, nbRoutes);
        int nbDeps = schedule.getTransitLines().values().stream().
                flatMap(l -> l.getRoutes().values().stream().flatMap(r -> r.getDepartures().values().stream())).collect(Collectors.toList()).size();
        Assert.assertEquals(3, nbDeps);

    }
}
