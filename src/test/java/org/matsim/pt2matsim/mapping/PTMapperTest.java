package org.matsim.pt2matsim.mapping;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;
import org.matsim.pt.utils.TransitScheduleValidator;
import org.matsim.pt2matsim.config.PublicTransitMappingConfigGroup;
import org.matsim.pt2matsim.config.PublicTransitMappingStrings;
import org.matsim.pt2matsim.tools.NetworkToolsTest;
import org.matsim.pt2matsim.tools.ScheduleTools;
import org.matsim.pt2matsim.tools.ScheduleToolsTest;

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
		config.setMaxLinkCandidateDistance(999.0);
		config.setCandidateDistanceMultiplier(1.0);

		PublicTransitMappingConfigGroup.TransportModeAssignment mraBus = new PublicTransitMappingConfigGroup.TransportModeAssignment("bus");
		mraBus.setNetworkModesStr("car,bus");
		config.addParameterSet(mraBus);

		return config;
	}

	@Before
	public void prepare() {
		ptmConfig = initPTMConfig();
		network = NetworkToolsTest.initNetwork();
		schedule = ScheduleToolsTest.initUnmappedSchedule();

		new PTMapper(schedule, network).run(ptmConfig);
	}

	@Test
	public void validateMappedSchedule() {
		Assert.assertTrue(TransitScheduleValidator.validateAll(schedule, network).isValid());
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

		TransitSchedule schedule2 = ScheduleToolsTest.initUnmappedSchedule();
		Network network2 = NetworkToolsTest.initNetwork();
		new PTMapper(schedule2, network2).run(ptmConfig2);

		// 1 loop link, 3 artificial links
		Assert.assertEquals(NetworkToolsTest.initNetwork().getLinks().size()+4, network2.getLinks().size());
		Assert.assertEquals(9, schedule2.getFacilities().size());
	}
	@Test
	public void noTransportModeAssignment() {
		PublicTransitMappingConfigGroup noTMAConfig = new PublicTransitMappingConfigGroup();
		noTMAConfig.getModesToKeepOnCleanUp().add("car");
		noTMAConfig.setNumOfThreads(2);
		noTMAConfig.setNLinkThreshold(4);
		noTMAConfig.setMaxLinkCandidateDistance(999.0);
		noTMAConfig.setCandidateDistanceMultiplier(1.0);

		TransitSchedule schedule2 = ScheduleToolsTest.initUnmappedSchedule();
		Network network2 = NetworkToolsTest.initNetwork();

		new PTMapper(schedule2, network2).run(noTMAConfig);

		// only artificial links
		for(TransitLine transitLine : schedule2.getTransitLines().values()) {
			for(TransitRoute transitRoute : transitLine.getRoutes().values()) {
				List<Id<Link>> linkIds = ScheduleTools.getTransitRouteLinkIds(transitRoute);
				for(Id<Link> linkId : linkIds) {
					Assert.assertTrue(linkId.toString().contains("pt_"));
				}
			}
		}
		// only artificial stop links
		for(TransitStopFacility transitStopFacility : schedule2.getFacilities().values()) {
			Assert.assertTrue(transitStopFacility.getLinkId().toString().contains("pt_"));
		}
	}
}