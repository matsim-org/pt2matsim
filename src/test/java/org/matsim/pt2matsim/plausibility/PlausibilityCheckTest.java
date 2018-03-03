package org.matsim.pt2matsim.plausibility;

import com.vividsolutions.jts.util.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.matsim.api.core.v01.network.Network;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt2matsim.plausibility.log.PlausibilityWarning;
import org.matsim.pt2matsim.run.CheckMappedSchedulePlausibility;
import org.matsim.pt2matsim.tools.NetworkTools;
import org.matsim.pt2matsim.tools.NetworkToolsTest;
import org.matsim.pt2matsim.tools.ScheduleTools;
import org.matsim.pt2matsim.tools.ScheduleToolsTest;

import java.io.File;
import java.util.Map;
import java.util.Set;

/**
 * @author polettif
 */
public class PlausibilityCheckTest {

	@Rule
	public TemporaryFolder temporaryFolder = new TemporaryFolder();

	private String testDir;
	private PlausibilityCheck check;

	@Before
	public void prepare() {
		testDir = temporaryFolder.getRoot().toString();

		TransitSchedule s = ScheduleToolsTest.initSchedule();
		Network n = NetworkToolsTest.initNetwork();

		check = new PlausibilityCheck(s, n, null);
		check.setDirectionChangeThreshold("bus", Math.PI * 95 / 180);
		check.setTtRange(0);
		check.runCheck();
	}

	@Test
	public void checks() {
		Map<PlausibilityWarning.Type, Set<PlausibilityWarning>> warnings = check.getWarnings();
		Assert.equals(0, warnings.get(PlausibilityWarning.Type.ArtificialLinkWarning).size());
		Assert.equals(0, warnings.get(PlausibilityWarning.Type.LoopWarning).size());
		Assert.equals(4, warnings.get(PlausibilityWarning.Type.DirectionChangeWarning).size());
		Assert.equals(0, warnings.get(PlausibilityWarning.Type.TravelTimeWarning).size());
	}

	@Test
	public void writeGeojson() {
		check.writeResultsGeojson(testDir + "plausibility.geojson");
		new File(testDir + "plausibility.geojson").delete();
	}

	@Test
	public void staticRun() {
		String scheduleFile = testDir + "testSchedule.xml";
		String networkFile = testDir + "testNetwork.xml";
		String outputfolder = testDir + "plausibility/";
		ScheduleTools.writeTransitSchedule(ScheduleToolsTest.initSchedule(), scheduleFile);
		NetworkTools.writeNetwork(NetworkToolsTest.initNetwork(), networkFile);

		CheckMappedSchedulePlausibility.run(scheduleFile, networkFile, "Atlantis", outputfolder);
	}

}