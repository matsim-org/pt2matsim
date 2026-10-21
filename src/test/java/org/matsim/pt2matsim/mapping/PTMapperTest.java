package org.matsim.pt2matsim.mapping;

import static org.matsim.pt2matsim.tools.ScheduleToolsTest.ROUTE_B;

import java.util.List;
import java.util.concurrent.ExecutionException;

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
	public void prepare() throws InterruptedException, ExecutionException {
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
	void artificialLinks() throws InterruptedException, ExecutionException {
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
	void noTransportModeAssignment() throws InterruptedException, ExecutionException {
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
	void modeSpecificRules() throws InterruptedException, ExecutionException {
		PublicTransitMappingConfigGroup ptmConfig2 = initPTMConfig();
		ptmConfig2.setModeSpecificRules("true");
		TransportModeParameterSet tmps = ptmConfig2.getParameterSetForMode("bus");
		tmps.setNumberOfLinkCandidates(4);
		tmps.setMaximumSearchDistance(3);
		TransitSchedule schedule2 = ScheduleToolsTest.initUnmappedSchedule();
		Network network2 = NetworkToolsTest.initNetwork();
		new PTMapper(schedule2, network2).run(ptmConfig2);
		
		Assertions.assertEquals(NetworkToolsTest.initNetwork().getLinks().size() + 4, network2.getLinks().size());
		
	}
	
	@Test
	void modeSpecificRules2() throws InterruptedException, ExecutionException {
		PublicTransitMappingConfigGroup ptmConfig2 = initPTMConfig();
		ptmConfig2.setModeSpecificRules("true");
		TransportModeParameterSet tmps = ptmConfig2.getParameterSetForMode("bus");
		tmps.setNumberOfLinkCandidates(2);
		tmps.setMaximumSearchDistance(300);
		tmps.setImposeStrictLinksRule(Boolean.parseBoolean("true"));
		
		TransitSchedule schedule2 = ScheduleToolsTest.initUnmappedSchedule();
		Network network2 = NetworkToolsTest.initNetwork();
		new PTMapper(schedule2, network2).run(ptmConfig2);
		
		Assertions.assertEquals(NetworkToolsTest.initNetwork().getLinks().size() + 13, network2.getLinks().size());
		
	}

	@Test
	void defaultConfig() {
		CreateDefaultPTMapperConfig.main(new String[]{"doc/defaultPTMapperConfig.xml"});
	}

	/**
	 * Equivalence test: mapping the same scenario with {@code boundedSearch=true} and
	 * {@code boundedSearch=false} must produce identical link sequences for every transit
	 * route. This locks in the central correctness claim of the bounded-search wire-through:
	 * for routes that are mapped, the bounded variant returns exactly the same routed path
	 * as the unbounded one (cutoff fires only on candidate pairs that would have been
	 * discarded downstream as exceeding {@code maxAllowedTravelCost} anyway).
	 * <p>
	 * Uses the same scenario as the {@link BeforeEach}-prepared instance (which already runs
	 * with the default {@code boundedSearch=true}), and reruns with the flag flipped to
	 * {@code false}.
	 */
	@Test
	void boundedSearchEquivalence() throws InterruptedException, ExecutionException {
		// schedule + network from @BeforeEach were mapped with boundedSearch=true (default).
		PublicTransitMappingConfigGroup ptmConfigUnbounded = initPTMConfig();
		ptmConfigUnbounded.setBoundedSearch(false);

		TransitSchedule scheduleUnbounded = ScheduleToolsTest.initUnmappedSchedule();
		Network networkUnbounded = NetworkToolsTest.initNetwork();
		new PTMapper(scheduleUnbounded, networkUnbounded).run(ptmConfigUnbounded);

		Assertions.assertEquals(schedule.getTransitLines().keySet(), scheduleUnbounded.getTransitLines().keySet(),
				"bounded and unbounded mappings must produce the same set of transit lines");

		for (TransitLine boundedLine : schedule.getTransitLines().values()) {
			TransitLine unboundedLine = scheduleUnbounded.getTransitLines().get(boundedLine.getId());
			Assertions.assertNotNull(unboundedLine, "missing line in unbounded schedule: " + boundedLine.getId());
			Assertions.assertEquals(boundedLine.getRoutes().keySet(), unboundedLine.getRoutes().keySet(),
					"route ids differ for line " + boundedLine.getId());

			for (TransitRoute boundedRoute : boundedLine.getRoutes().values()) {
				TransitRoute unboundedRoute = unboundedLine.getRoutes().get(boundedRoute.getId());
				List<Id<Link>> boundedLinkIds = ScheduleTools.getTransitRouteLinkIds(boundedRoute);
				List<Id<Link>> unboundedLinkIds = ScheduleTools.getTransitRouteLinkIds(unboundedRoute);
				Assertions.assertEquals(unboundedLinkIds, boundedLinkIds,
						"link sequence differs for line=" + boundedLine.getId() + " route=" + boundedRoute.getId());
			}
		}
	}

}