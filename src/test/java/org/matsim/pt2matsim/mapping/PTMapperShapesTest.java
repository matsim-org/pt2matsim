package org.matsim.pt2matsim.mapping;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.utils.TransitScheduleValidator;
import org.matsim.pt2matsim.config.PublicTransitMappingConfigGroup;
import org.matsim.pt2matsim.config.PublicTransitMappingStrings;
import org.matsim.pt2matsim.mapping.networkRouter.ScheduleRoutersFactory;
import org.matsim.pt2matsim.mapping.networkRouter.ScheduleRoutersGtfsShapes;
import org.matsim.pt2matsim.tools.NetworkToolsTest;
import org.matsim.pt2matsim.tools.ScheduleTools;
import org.matsim.pt2matsim.tools.ScheduleToolsTest;
import org.matsim.pt2matsim.tools.ShapeToolsTest;
import org.matsim.pt2matsim.tools.debug.ScheduleCleaner;
import org.matsim.pt2matsim.tools.lib.RouteShape;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import static org.matsim.pt2matsim.mapping.PTMapperTest.initPTMConfig;

/**
 * @author polettif
 */
class PTMapperShapesTest {

	public Network network;
	public TransitSchedule schedule;
	public PublicTransitMappingConfigGroup ptmConfig;

	@BeforeEach
	public void prepare() throws InterruptedException, ExecutionException {
		ptmConfig = initPTMConfig();
		network = NetworkToolsTest.initNetwork();
		schedule = ScheduleToolsTest.initUnmappedSchedule();

		Map<Id<RouteShape>, RouteShape> shapes = ShapeToolsTest.initShapes();
		ScheduleRoutersFactory scheduleRoutersFactory = new ScheduleRoutersGtfsShapes.Factory(schedule, network, shapes, ptmConfig.getTransportModeAssignment(), PublicTransitMappingConfigGroup.TravelCostType.linkLength, 10.0, 99);

		new PTMapper(schedule, network).run(ptmConfig, null, scheduleRoutersFactory);

		ScheduleCleaner.removeNotUsedStopFacilities(schedule);
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
	void linkSequences() {
		TransitSchedule initSchedule = ScheduleToolsTest.initSchedule();

		for(TransitLine l : schedule.getTransitLines().values()) {
			for(TransitRoute r : l.getRoutes().values()) {
				TransitRoute initRoute = initSchedule.getTransitLines().get(l.getId()).getRoutes().get(r.getId());
				List<Id<Link>> initLinkIds = ScheduleTools.getTransitRouteLinkIds(initRoute);
				List<Id<Link>> linkIds = ScheduleTools.getTransitRouteLinkIds(r);
				Assertions.assertEquals(initLinkIds, linkIds);
			}
		}
	}

	/**
	 * Equivalence test on the shapes-based mapping (uses {@link ScheduleRoutersGtfsShapes}
	 * rather than the standard router). {@code boundedSearch=true} (the @BeforeEach default)
	 * and {@code boundedSearch=false} must produce identical link sequences for every route.
	 */
	@Test
	void boundedSearchEquivalence() throws InterruptedException, ExecutionException {
		PublicTransitMappingConfigGroup ptmConfigUnbounded = initPTMConfig();
		ptmConfigUnbounded.setBoundedSearch(false);

		TransitSchedule scheduleUnbounded = ScheduleToolsTest.initUnmappedSchedule();
		Network networkUnbounded = NetworkToolsTest.initNetwork();
		Map<Id<RouteShape>, RouteShape> shapesUnbounded = ShapeToolsTest.initShapes();
		ScheduleRoutersFactory factoryUnbounded = new ScheduleRoutersGtfsShapes.Factory(scheduleUnbounded,
				networkUnbounded, shapesUnbounded, ptmConfigUnbounded.getTransportModeAssignment(),
				PublicTransitMappingConfigGroup.TravelCostType.linkLength, 10.0, 99);

		new PTMapper(scheduleUnbounded, networkUnbounded).run(ptmConfigUnbounded, null, factoryUnbounded);
		ScheduleCleaner.removeNotUsedStopFacilities(scheduleUnbounded);

		Assertions.assertEquals(schedule.getTransitLines().keySet(), scheduleUnbounded.getTransitLines().keySet());

		for (TransitLine boundedLine : schedule.getTransitLines().values()) {
			TransitLine unboundedLine = scheduleUnbounded.getTransitLines().get(boundedLine.getId());
			Assertions.assertNotNull(unboundedLine);
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