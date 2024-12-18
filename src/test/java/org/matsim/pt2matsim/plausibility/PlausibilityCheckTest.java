package org.matsim.pt2matsim.plausibility;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.matsim.api.core.v01.network.Network;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt2matsim.plausibility.log.PlausibilityWarning;
import org.matsim.pt2matsim.run.CheckMappedSchedulePlausibility;
import org.matsim.pt2matsim.tools.NetworkTools;
import org.matsim.pt2matsim.tools.NetworkToolsTest;
import org.matsim.pt2matsim.tools.ScheduleTools;
import org.matsim.pt2matsim.tools.ScheduleToolsTest;

import java.io.File;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

/**
 * @author polettif
 */
class PlausibilityCheckTest {

	@TempDir
	public Path temporaryFolder;

	private String testDir;
	private PlausibilityCheck check;

	@BeforeEach
	public void prepare() {
		testDir = temporaryFolder.toString();

		TransitSchedule s = ScheduleToolsTest.initSchedule();
		Network n = NetworkToolsTest.initNetwork();

		check = new PlausibilityCheck(s, n, null);
		check.setDirectionChangeThreshold("bus", Math.PI * 95 / 180);
		check.setTtRange(0);
		check.runCheck();
	}

	@Test
	void checks() {
		Map<PlausibilityWarning.Type, Set<PlausibilityWarning>> warnings = check.getWarnings();
		Assertions.assertEquals(0, warnings.get(PlausibilityWarning.Type.ArtificialLinkWarning).size());
		Assertions.assertEquals(0, warnings.get(PlausibilityWarning.Type.LoopWarning).size());
		Assertions.assertEquals(4, warnings.get(PlausibilityWarning.Type.DirectionChangeWarning).size());
		Assertions.assertEquals(0, warnings.get(PlausibilityWarning.Type.TravelTimeWarning).size());
	}

	@Test
	void writeGeojson() {
		check.writeResultsGeojson(testDir + "plausibility.geojson");
		new File(testDir + "plausibility.geojson").delete();
	}

	@Test
	void staticRun() {
		String scheduleFile = testDir + "testSchedule.xml";
		String networkFile = testDir + "testNetwork.xml";
		String outputfolder = testDir + "plausibility/";
		ScheduleTools.writeTransitSchedule(ScheduleToolsTest.initSchedule(), scheduleFile);
		NetworkTools.writeNetwork(NetworkToolsTest.initNetwork(), networkFile);

		CheckMappedSchedulePlausibility.run(scheduleFile, networkFile, "Atlantis", outputfolder);
	}

}