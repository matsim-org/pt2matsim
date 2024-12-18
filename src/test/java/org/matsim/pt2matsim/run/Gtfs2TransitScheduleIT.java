package org.matsim.pt2matsim.run;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.nio.file.Paths;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;
import org.matsim.core.utils.io.IOUtils;
import org.matsim.core.utils.misc.CRCChecksum;

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
		Gtfs2TransitSchedule.run("test/gtfs-feed/", "20181005", TransformationFactory.CH1903_LV03_Plus, OUTPUTDIR + "schedule.xml", OUTPUTDIR + "vehicles.xml", null);
		Assertions.assertEquals(CRCChecksum.getCRCFromFile("test/Gtfs2TransitScheduleIT/testNoAdditionalInfo/schedule.xml"), CRCChecksum.getCRCFromFile(OUTPUTDIR + "schedule.xml"));
		Assertions.assertEquals(CRCChecksum.getCRCFromFile("test/Gtfs2TransitScheduleIT/testNoAdditionalInfo/vehicles.xml"), CRCChecksum.getCRCFromFile(OUTPUTDIR + "vehicles.xml"));
		Assertions.assertFalse(new File(OUTPUTDIR + "info.csv").exists());
	}
	
	@Test
	void testInfoInSchedule() {
		Gtfs2TransitSchedule.run("test/gtfs-feed/", "20181005", TransformationFactory.CH1903_LV03_Plus, OUTPUTDIR + "schedule.xml", OUTPUTDIR + "vehicles.xml", "schedule");
		Assertions.assertEquals(CRCChecksum.getCRCFromFile("test/Gtfs2TransitScheduleIT/testInfoInSchedule/schedule.xml"), CRCChecksum.getCRCFromFile(OUTPUTDIR + "schedule.xml"));
		Assertions.assertEquals(CRCChecksum.getCRCFromFile("test/Gtfs2TransitScheduleIT/testNoAdditionalInfo/vehicles.xml"), CRCChecksum.getCRCFromFile(OUTPUTDIR + "vehicles.xml"));
		Assertions.assertFalse(new File(OUTPUTDIR + "info.csv").exists());
	}
	
	@Test
	void testInfoInSeparateFile() {
		Gtfs2TransitSchedule.run("test/gtfs-feed/", "20181005", TransformationFactory.CH1903_LV03_Plus, OUTPUTDIR + "schedule.xml", OUTPUTDIR + "vehicles.xml", OUTPUTDIR + "info.csv");
		Assertions.assertEquals(CRCChecksum.getCRCFromFile("test/Gtfs2TransitScheduleIT/testNoAdditionalInfo/schedule.xml"), CRCChecksum.getCRCFromFile(OUTPUTDIR + "schedule.xml"));
		Assertions.assertEquals(CRCChecksum.getCRCFromFile("test/Gtfs2TransitScheduleIT/testNoAdditionalInfo/vehicles.xml"), CRCChecksum.getCRCFromFile(OUTPUTDIR + "vehicles.xml"));
		Assertions.assertEquals(CRCChecksum.getCRCFromFile("test/Gtfs2TransitScheduleIT/testInfoInSeparateFile/info.csv"), CRCChecksum.getCRCFromFile(OUTPUTDIR + "info.csv"));
	}
	
	@Test
	void testNoVehicles() {
		Gtfs2TransitSchedule.run("test/gtfs-feed/", "20181005", TransformationFactory.CH1903_LV03_Plus, OUTPUTDIR + "schedule.xml", null, null);
		Assertions.assertEquals(CRCChecksum.getCRCFromFile("test/Gtfs2TransitScheduleIT/testNoAdditionalInfo/schedule.xml"), CRCChecksum.getCRCFromFile(OUTPUTDIR + "schedule.xml"));
		Assertions.assertFalse(new File(OUTPUTDIR + "vehicles.xml").exists());
		Assertions.assertFalse(new File(OUTPUTDIR + "info.csv").exists());
	}

}
