package org.matsim.pt2matsim.run;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.nio.file.Paths;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ProjectionUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;
import org.matsim.core.utils.io.IOUtils;
import org.matsim.core.utils.misc.CRCChecksum;
import org.matsim.pt.transitSchedule.api.TransitScheduleReader;

class Gtfs2TransitScheduleIT {
	
	private final String OUTPUTDIR = "test/Gtfs2TransitScheduleIT/output/";
	
	@BeforeEach
	void setUp() {
		if (new File(OUTPUTDIR).exists()) {
			IOUtils.deleteDirectoryRecursively(Paths.get(OUTPUTDIR));
		}
		new File(OUTPUTDIR).mkdir();
	}

	@Test
	void testNoAdditionalInfo() {
		Gtfs2TransitSchedule.run("test/gtfs-feed/", "20181005", TransformationFactory.CH1903_LV03_Plus, OUTPUTDIR + "schedule.xml", OUTPUTDIR + "vehicles.xml", null, false);
		Assertions.assertEquals(CRCChecksum.getCRCFromFile("test/Gtfs2TransitScheduleIT/testNoAdditionalInfo/schedule.xml"), CRCChecksum.getCRCFromFile(OUTPUTDIR + "schedule.xml"));
		Assertions.assertEquals(CRCChecksum.getCRCFromFile("test/Gtfs2TransitScheduleIT/testNoAdditionalInfo/vehicles.xml"), CRCChecksum.getCRCFromFile(OUTPUTDIR + "vehicles.xml"));
		Assertions.assertFalse(new File(OUTPUTDIR + "info.csv").exists());
	}
	
	@Test
	void testInfoInSchedule() {
		Gtfs2TransitSchedule.run("test/gtfs-feed/", "20181005", TransformationFactory.CH1903_LV03_Plus, OUTPUTDIR + "schedule.xml", OUTPUTDIR + "vehicles.xml", "schedule", false);
		Assertions.assertEquals(CRCChecksum.getCRCFromFile("test/Gtfs2TransitScheduleIT/testInfoInSchedule/schedule.xml"), CRCChecksum.getCRCFromFile(OUTPUTDIR + "schedule.xml"));
		Assertions.assertEquals(CRCChecksum.getCRCFromFile("test/Gtfs2TransitScheduleIT/testNoAdditionalInfo/vehicles.xml"), CRCChecksum.getCRCFromFile(OUTPUTDIR + "vehicles.xml"));
		Assertions.assertFalse(new File(OUTPUTDIR + "info.csv").exists());
	}
	
	@Test
	void testInfoInSeparateFile() {
		Gtfs2TransitSchedule.run("test/gtfs-feed/", "20181005", TransformationFactory.CH1903_LV03_Plus, OUTPUTDIR + "schedule.xml", OUTPUTDIR + "vehicles.xml", OUTPUTDIR + "info.csv", false);
		Assertions.assertEquals(CRCChecksum.getCRCFromFile("test/Gtfs2TransitScheduleIT/testNoAdditionalInfo/schedule.xml"), CRCChecksum.getCRCFromFile(OUTPUTDIR + "schedule.xml"));
		Assertions.assertEquals(CRCChecksum.getCRCFromFile("test/Gtfs2TransitScheduleIT/testNoAdditionalInfo/vehicles.xml"), CRCChecksum.getCRCFromFile(OUTPUTDIR + "vehicles.xml"));
		Assertions.assertEquals(CRCChecksum.getCRCFromFile("test/Gtfs2TransitScheduleIT/testInfoInSeparateFile/info.csv"), CRCChecksum.getCRCFromFile(OUTPUTDIR + "info.csv"));
	}
	
	@Test
	void testNoVehicles() {
		Gtfs2TransitSchedule.run("test/gtfs-feed/", "20181005", TransformationFactory.CH1903_LV03_Plus, OUTPUTDIR + "schedule.xml", null, null, false);
		Assertions.assertEquals(CRCChecksum.getCRCFromFile("test/Gtfs2TransitScheduleIT/testNoAdditionalInfo/schedule.xml"), CRCChecksum.getCRCFromFile(OUTPUTDIR + "schedule.xml"));
		Assertions.assertFalse(new File(OUTPUTDIR + "vehicles.xml").exists());
		Assertions.assertFalse(new File(OUTPUTDIR + "info.csv").exists());
	}

	@Test
	void testCrsInSchedule() {
		Gtfs2TransitSchedule.run("test/gtfs-feed/", "20181005", TransformationFactory.CH1903_LV03_Plus, OUTPUTDIR + "schedule.xml", null, null, true);
		
		Config config = ConfigUtils.createConfig();
		config.global().setCoordinateSystem(TransformationFactory.CH1903_LV03_Plus);

		Scenario scenario = ScenarioUtils.createScenario(config);
		new TransitScheduleReader(scenario).readFile(OUTPUTDIR + "schedule.xml");

		assertEquals(TransformationFactory.CH1903_LV03_Plus, ProjectionUtils.getCRS(scenario.getTransitSchedule()));
	}
}
