package org.matsim.pt2matsim.editor;

import org.junit.Assert;
import org.junit.Test;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt2matsim.tools.NetworkToolsTest;
import org.matsim.pt2matsim.tools.ScheduleTools;

import java.util.ArrayList;
import java.util.List;

import static org.matsim.pt2matsim.tools.ScheduleToolsTest.*;

/**
 * @author polettif
 */
public class BasicScheduleEditorTest {

	/**
	 * Possible Commands:
	 * - Reroute TransitRoute via new Link
	 * ["rerouteViaLink"] [TransitLineId] [TransitRouteId] [oldLinkId] [newLinkId]
	 */
	@Test
	public void rerouteViaLink() {
		TransitSchedule schedule = initSchedule();
		Network network = NetworkToolsTest.initNetwork();

		String[] cmd = new String[]{ScheduleEditor.RR_VIA_LINK, LINE_B.toString(), ROUTE_B.toString(), "CX", "CB"};

		new BasicScheduleEditor(schedule, network).executeCmdLine(cmd);

		List<Id<Link>> linkIds = ScheduleTools.getTransitRouteLinkIds(schedule.getTransitLines().get(LINE_B).getRoutes().get(ROUTE_B));

		List<Id<Link>> linkIdsExpected = new ArrayList<>();
		linkIdsExpected.add(Id.createLinkId("EW"));
		linkIdsExpected.add(Id.createLinkId("WD"));
		linkIdsExpected.add(Id.createLinkId("DC"));
		linkIdsExpected.add(Id.createLinkId("CB"));
		linkIdsExpected.add(Id.createLinkId("BX"));
		linkIdsExpected.add(Id.createLinkId("XA"));
		linkIdsExpected.add(Id.createLinkId("AH"));
		linkIdsExpected.add(Id.createLinkId("HZ"));
		linkIdsExpected.add(Id.createLinkId("ZI"));
		linkIdsExpected.add(Id.createLinkId("IB"));

		Assert.assertEquals(linkIdsExpected, linkIds);
	}

	/**
	 * - Changes the referenced link of a stopfacility. Effectively creates a new child stop facility.
	 * ["changeRefLink"] [StopFacilityId] [newlinkId]
	 * ["changeRefLink"] [TransitLineId] [TransitRouteId] [ParentId] [newlinkId]
	 * ["changeRefLink"] ["allTransitRoutesOnLink"] [linkId] [ParentId] [newlinkId]
	 */
	@Test
	public void changeRefLink() {
		TransitSchedule schedule = initSchedule();
		Network network = NetworkToolsTest.initNetwork();

		String[] cmd = new String[]{ScheduleEditor.CHANGE_REF_LINK, ScheduleEditor.ALL_TRANSIT_ROUTES_ON_LINK, "XA", "stop3.link:XA", "XB"};

		new BasicScheduleEditor(schedule, network).executeCmdLine(cmd);

		List<Id<Link>> linkIds = ScheduleTools.getTransitRouteLinkIds(schedule.getTransitLines().get(LINE_B).getRoutes().get(ROUTE_B));

		List<Id<Link>> linkIdsExpected = new ArrayList<>();
		linkIdsExpected.add(Id.createLinkId("EW"));
		linkIdsExpected.add(Id.createLinkId("WD"));
		linkIdsExpected.add(Id.createLinkId("DA"));
		linkIdsExpected.add(Id.createLinkId("AX"));
		linkIdsExpected.add(Id.createLinkId("XB"));
		linkIdsExpected.add(Id.createLinkId("BA"));
		linkIdsExpected.add(Id.createLinkId("AH"));
		linkIdsExpected.add(Id.createLinkId("HZ"));
		linkIdsExpected.add(Id.createLinkId("ZI"));
		linkIdsExpected.add(Id.createLinkId("IB"));

		Assert.assertEquals(linkIdsExpected, linkIds);
	}

}