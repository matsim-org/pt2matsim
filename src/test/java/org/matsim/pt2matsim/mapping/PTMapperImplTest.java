package org.matsim.pt2matsim.mapping;

import org.junit.Before;
import org.junit.Test;
import org.matsim.api.core.v01.network.Network;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt2matsim.config.PublicTransitMappingConfigGroup;
import org.matsim.pt2matsim.tools.NetworkToolsTest;
import org.matsim.pt2matsim.tools.ScheduleToolsTest;
import org.matsim.pt2matsim.tools.debug.ScheduleCleaner;

/**
 * @author polettif
 */
public class PTMapperImplTest {

	public Network network;
	public TransitSchedule schedule;
	public PublicTransitMappingConfigGroup ptmConfig;

	@Before
	public void prepare() {
		ptmConfig = initPTMConfig();
		network = NetworkToolsTest.initNetwork();
		schedule = ScheduleToolsTest.initSchedule();
		ScheduleCleaner.removeMapping(schedule);
	}

	@Test
	public void run() throws Exception {
		PTMapper ptm = new PTMapperImpl(ptmConfig, schedule, network);
		ptm.run();
	}

	public static PublicTransitMappingConfigGroup initPTMConfig() {
		PublicTransitMappingConfigGroup config = new PublicTransitMappingConfigGroup();
		config.getModesToKeepOnCleanUp().add("car");
		PublicTransitMappingConfigGroup.LinkCandidateCreatorParams lccParamsBus = new PublicTransitMappingConfigGroup.LinkCandidateCreatorParams("bus");
		lccParamsBus.setNetworkModesStr("car");
		lccParamsBus.setMaxLinkCandidateDistance(5.0);
		config.addParameterSet(lccParamsBus);

		PublicTransitMappingConfigGroup.ModeRoutingAssignment mraBus = new PublicTransitMappingConfigGroup.ModeRoutingAssignment("bus");
		mraBus.setNetworkModesStr("car,bus");
		config.addParameterSet(mraBus);

		config.addParameterSet(new PublicTransitMappingConfigGroup.ManualLinkCandidates());

		return config;
	}
}