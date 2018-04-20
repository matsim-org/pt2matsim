package org.matsim.pt2matsim.hafas;

import org.junit.Assert;
import org.junit.Test;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt2matsim.tools.ScheduleTools;
import org.matsim.vehicles.VehicleUtils;
import org.matsim.vehicles.Vehicles;

import java.io.IOException;
import java.util.stream.Collectors;

/**
 * Created by Johannes Lieberherr on 18.04.2018.
 */
public class HafasFplanTest {
    @Test
    public void simpleFplanTest() throws IOException {

        TransitSchedule schedule = ScheduleTools.createSchedule();
        Vehicles vehicles = VehicleUtils.createVehiclesContainer();
        String hafasFolder = "test/FPLAN_HAFAS/";
        String cs = "EPSG:2056";
        CoordinateTransformation ct = TransformationFactory.getCoordinateTransformation("WGS84", cs);

        HafasConverter.run(hafasFolder, schedule, ct, vehicles);

        int nbRoutes = schedule.getTransitLines().values().stream().flatMap(l -> l.getRoutes().values().stream()).collect(Collectors.toList()).size();
        Assert.assertEquals(2, nbRoutes);
    }
}
