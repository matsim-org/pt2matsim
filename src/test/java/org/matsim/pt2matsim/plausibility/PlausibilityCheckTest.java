package org.matsim.pt2matsim.plausibility;

import com.vividsolutions.jts.util.Assert;
import org.junit.Test;
import org.matsim.api.core.v01.network.Network;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt2matsim.plausibility.log.PlausibilityWarning;
import org.matsim.pt2matsim.tools.NetworkToolsTest;
import org.matsim.pt2matsim.tools.ScheduleToolsTest;

import java.util.Map;
import java.util.Set;

/**
 * @author polettif
 */
public class PlausibilityCheckTest {

	@Test
	public void runCheck() {
		TransitSchedule s = ScheduleToolsTest.initSchedule();
		Network n = NetworkToolsTest.initNetwork();

		PlausibilityCheck check = new PlausibilityCheck(s, n, null);
		check.setDirectionChangeThreshold("bus", Math.PI * 95 / 180);
		check.setTtRange(0);
		check.runCheck();

		Map<PlausibilityWarning.Type, Set<PlausibilityWarning>> warnings = check.getWarnings();
		Assert.equals(0, warnings.get(PlausibilityWarning.Type.ArtificialLinkWarning).size());
		Assert.equals(0, warnings.get(PlausibilityWarning.Type.LoopWarning).size());
		Assert.equals(4, warnings.get(PlausibilityWarning.Type.DirectionChangeWarning).size());
		Assert.equals(0, warnings.get(PlausibilityWarning.Type.TravelTimeWarning).size());
	}

}