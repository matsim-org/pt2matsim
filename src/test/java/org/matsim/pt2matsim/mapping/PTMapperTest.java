package org.matsim.pt2matsim.mapping;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.config.ConfigGroup;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt2matsim.config.PublicTransitMappingConfigGroup;
import org.matsim.pt2matsim.config.PublicTransitMappingStrings;
import org.matsim.pt2matsim.tools.NetworkToolsTest;
import org.matsim.pt2matsim.tools.ScheduleTools;
import org.matsim.pt2matsim.tools.ScheduleToolsTest;
import org.matsim.pt2matsim.tools.debug.ScheduleCleaner;

import java.util.Collection;
import java.util.List;

import static org.matsim.pt2matsim.tools.ScheduleToolsTest.ROUTE_B;

/**
 * @author polettif
 */
public class PTMapperTest {

	public Network network;
	public TransitSchedule schedule;
	public PublicTransitMappingConfigGroup ptmConfig;

	public static PublicTransitMappingConfigGroup initPTMConfig() {
		PublicTransitMappingConfigGroup config = new PublicTransitMappingConfigGroup();
		config.getModesToKeepOnCleanUp().add("car");
		config.setNumOfThreads(2);
		config.setNLinkThreshold(4);
		config.setMaxLinkCandidateDistance(99.0);
		config.setCandidateDistanceMultiplier(1.0);

		PublicTransitMappingConfigGroup.ModeRoutingAssignment mraBus = new PublicTransitMappingConfigGroup.ModeRoutingAssignment("bus");
		mraBus.setNetworkModesStr("car,bus");
		config.addParameterSet(mraBus);

		return config;
	}

	@Before
	public void prepare() {
		ptmConfig = initPTMConfig();
		network = NetworkToolsTest.initNetwork();
		schedule = ScheduleToolsTest.initSchedule();
		ScheduleCleaner.combineChildStopsToParentStop(schedule);
		ScheduleCleaner.removeMapping(schedule);
		ScheduleCleaner.removeNotUsedStopFacilities(schedule);

		new PTMapper(ptmConfig, schedule, network).run();

//		NetworkTools.writeNetwork(network, "test/simple/outputNetwork.xml");
//		ScheduleTools.writeTransitSchedule(schedule, "test/simple/outpuSchedule.xml");
	}

	@Test
	public void allowedModes() {
		for(Link l : network.getLinks().values()) {
			Assert.assertFalse(l.getAllowedModes().contains(PublicTransitMappingStrings.ARTIFICIAL_LINK_MODE));
		}
	}

	@Test
	public void numberOfStopFacilities() {
		Assert.assertEquals(10, schedule.getFacilities().size());
	}

	@Test
	public void linkSequences() {
		TransitSchedule initSchedule = ScheduleToolsTest.initSchedule();

		for(TransitLine l : schedule.getTransitLines().values()) {
			for(TransitRoute r : l.getRoutes().values()) {
				TransitRoute initRoute = initSchedule.getTransitLines().get(l.getId()).getRoutes().get(r.getId());
				List<Id<Link>> initLinkIds = ScheduleTools.getTransitRouteLinkIds(initRoute);
				List<Id<Link>> linkIds = ScheduleTools.getTransitRouteLinkIds(r);
				if(!r.getId().equals(ROUTE_B)) { // route B cantt be guessed by the mapper because there's not enough information
					Assert.assertEquals(initLinkIds, linkIds);
				}
			}
		}
	}

	@Test
	public void artificialLinks() {
		PublicTransitMappingConfigGroup ptmConfig2 = initPTMConfig();
		ptmConfig2.setMaxLinkCandidateDistance(3);

		TransitSchedule schedule2 = ScheduleToolsTest.initSchedule();
		Network network2 = NetworkToolsTest.initNetwork();
		ScheduleCleaner.combineChildStopsToParentStop(schedule2);
		ScheduleCleaner.removeMapping(schedule2);
		ScheduleCleaner.removeNotUsedStopFacilities(schedule2);
		new PTMapper(ptmConfig2, schedule2, network2).run();

		// 1 loop link, 3 artificial links
		Assert.assertEquals(NetworkToolsTest.initNetwork().getLinks().size()+4, network2.getLinks().size());
		Assert.assertEquals(9, schedule2.getFacilities().size());
	}
}