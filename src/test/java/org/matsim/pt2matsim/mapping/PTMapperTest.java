package org.matsim.pt2matsim.mapping;

import static org.matsim.pt2matsim.tools.ScheduleToolsTest.ROUTE_B;

import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import org.matsim.pt2matsim.config.TransportModeParameterSet;
import org.matsim.pt2matsim.run.CreateDefaultPTMapperConfig;
import org.matsim.pt2matsim.tools.NetworkToolsTest;
import org.matsim.pt2matsim.tools.ScheduleTools;
import org.matsim.pt2matsim.tools.ScheduleToolsTest;

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

		TransportModeParameterSet mraBus = new TransportModeParameterSet("bus");
		mraBus.setNetworkModesStr("car,bus");
		config.addParameterSet(mraBus);

		return config;
	}

	@BeforeEach
	public void prepare() {
		ptmConfig = initPTMConfig();
		network = NetworkToolsTest.initNetwork();
		schedule = ScheduleToolsTest.initUnmappedSchedule();

		new PTMapper(schedule, network).run(ptmConfig);
	}

	@Test
	void validateMappedSchedule() {
		Assertions.assertTrue(TransitScheduleValidator.validateAll(schedule, network).isValid());
	}


	@Test
	void allowedModes() {
		for(Link l : network.getLinks().values()) {
			Assertions.assertFalse(l.getAllowedModes().contains(PublicTransitMappingStrings.ARTIFICIAL_LINK_MODE));
		}
	}

	@Test
	void numberOfStopFacilities() {
		Assertions.assertEquals(10, schedule.getFacilities().size());
	}

	@Test
	void linkSequences() {
		TransitSchedule initSchedule = ScheduleToolsTest.initSchedule();

		for(TransitLine l : schedule.getTransitLines().values()) {
			for(TransitRoute r : l.getRoutes().values()) {
				TransitRoute initRoute = initSchedule.getTransitLines().get(l.getId()).getRoutes().get(r.getId());
				List<Id<Link>> initLinkIds = ScheduleTools.getTransitRouteLinkIds(initRoute);
				List<Id<Link>> linkIds = ScheduleTools.getTransitRouteLinkIds(r);
				if(!r.getId().equals(ROUTE_B)) { // route B cantt be guessed by the mapper because there's not enough information
					Assertions.assertEquals(initLinkIds, linkIds);
				}
			}
		}
	}

	@Test
	void artificialLinks() {
		PublicTransitMappingConfigGroup ptmConfig2 = initPTMConfig();
		ptmConfig2.setMaxLinkCandidateDistance(3);

		TransitSchedule schedule2 = ScheduleToolsTest.initUnmappedSchedule();
		Network network2 = NetworkToolsTest.initNetwork();
		new PTMapper(schedule2, network2).run(ptmConfig2);

		// 1 loop link, 3 artificial links
		Assertions.assertEquals(NetworkToolsTest.initNetwork().getLinks().size() + 4, network2.getLinks().size());
		Assertions.assertEquals(9, schedule2.getFacilities().size());
	}
	@Test
	void noTransportModeAssignment() {
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
					Assertions.assertTrue(linkId.toString().contains("pt_"));
				}
			}
		}
		// only artificial stop links
		for(TransitStopFacility transitStopFacility : schedule2.getFacilities().values()) {
			Assertions.assertTrue(transitStopFacility.getLinkId().toString().contains("pt_"));
		}
	}

	@Test
	void defaultConfig() {
		CreateDefaultPTMapperConfig.main(new String[]{"doc/defaultPTMapperConfig.xml"});
	}

}